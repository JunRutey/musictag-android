// JNI bridge between Kotlin (com.xjcheng.musictageditor.core.tag.NativeTagBridge) and
// upstream TagLib 2.x. Read-only in M1; the write path lands in M2 on the same surface.
//
// Design notes:
//  * One JNI call returns everything about a file as a flat String[], so Kotlin needs
//    no JSON dependency and the file is parsed once.
//  * Pictures travel base64-encoded inside that array; covers are small and this keeps
//    the contract to a single, easily-tested method.
//  * Strings are converted through UTF-16 so tags containing emoji or other
//    supplementary-plane characters survive (NewStringUTF would mangle them).
//  * Include paths follow TagLib 2.x's build-tree layout (<ape/apefile.h>), which is what
//    the FetchContent source tree provides; the 1.x flat layout is not supported.

#include <jni.h>

#include <algorithm>
#include <cctype>
#include <string>
#include <utility>
#include <vector>

#include <ape/apefile.h>
#include <ape/apeproperties.h>
#include <ape/apetag.h>
#include <asf/asffile.h>
#include <asf/asfproperties.h>
#include <audioproperties.h>
#include <fileref.h>
#include <flac/flacfile.h>
#include <flac/flacproperties.h>
#include <mp4/mp4coverart.h>
#include <mp4/mp4file.h>
#include <mp4/mp4tag.h>
#include <mpeg/id3v2/frames/attachedpictureframe.h>
#include <mpeg/id3v2/frames/commentsframe.h>
#include <mpeg/id3v2/frames/textidentificationframe.h>
#include <mpeg/id3v2/frames/unsynchronizedlyricsframe.h>
#include <mpeg/id3v2/id3v2tag.h>
#include <mpeg/id3v1/id3v1tag.h>
#include <mpeg/mpegfile.h>
#include <ogg/oggfile.h>
#include <ogg/vorbis/vorbisfile.h>
#include <ogg/xiphcomment.h>
#include <tag.h>
#include <toolkit/tpropertymap.h>

namespace {

using TagLib::AudioProperties;
using TagLib::ByteVector;
using TagLib::File;
using TagLib::FileRef;
using TagLib::PropertyMap;
using TagLib::String;
using TagLib::StringList;
using TagLib::Tag;

struct Picture {
    std::string mime;
    ByteVector data;
};

std::string utf8(const String &value) { return value.to8Bit(true); }

jstring toJavaString(JNIEnv *env, const char *data, size_t size) {
    std::string text(data, size);
    std::vector<jchar> utf16;
    utf16.reserve(text.size());
    size_t i = 0;
    while (i < text.size()) {
        const unsigned char lead = static_cast<unsigned char>(text[i]);
        unsigned int cp = 0;
        size_t extra = 0;
        if (lead < 0x80) {
            cp = lead;
        } else if ((lead >> 5) == 0x06) {
            cp = lead & 0x1F;
            extra = 1;
        } else if ((lead >> 4) == 0x0E) {
            cp = lead & 0x0F;
            extra = 2;
        } else if ((lead >> 3) == 0x1E) {
            cp = lead & 0x07;
            extra = 3;
        } else {
            utf16.push_back(0xFFFD);  // invalid byte
            ++i;
            continue;
        }
        if (i + extra >= text.size()) {
            utf16.push_back(0xFFFD);
            break;
        }
        for (size_t k = 1; k <= extra; ++k) {
            cp = (cp << 6) | (static_cast<unsigned char>(text[i + k]) & 0x3F);
        }
        i += extra + 1;
        if (cp <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(cp));
        } else {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

jstring toJavaString(JNIEnv *env, const std::string &value) {
    return toJavaString(env, value.data(), value.size());
}

std::string base64(const ByteVector &bytes) {
    static const char table[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    const auto *p = reinterpret_cast<const unsigned char *>(bytes.data());
    const size_t n = bytes.size();
    std::string out;
    out.reserve(((n + 2) / 3) * 4);
    for (size_t i = 0; i < n; i += 3) {
        const unsigned a = p[i];
        const unsigned b = (i + 1 < n) ? p[i + 1] : 0;
        const unsigned c = (i + 2 < n) ? p[i + 2] : 0;
        out.push_back(table[(a >> 2) & 0x3F]);
        out.push_back(table[((a << 4) | (b >> 4)) & 0x3F]);
        out.push_back((i + 1 < n) ? table[((b << 2) | (c >> 6)) & 0x3F] : '=');
        out.push_back((i + 2 < n) ? table[c & 0x3F] : '=');
    }
    return out;
}

std::string firstProperty(const PropertyMap &map, const char *key) {
    const auto it = map.find(String(key, String::UTF8));
    if (it == map.end() || it->second.isEmpty()) {
        return {};
    }
    return utf8(it->second.front());
}

std::string detectContainer(File *file) {
    if (dynamic_cast<TagLib::MPEG::File *>(file)) return "MPEG";
    if (dynamic_cast<TagLib::FLAC::File *>(file)) return "FLAC";
    if (dynamic_cast<TagLib::MP4::File *>(file)) return "MP4";
    if (dynamic_cast<TagLib::APE::File *>(file)) return "APE";
    if (dynamic_cast<TagLib::Ogg::Vorbis::File *>(file)) return "OggVorbis";
    if (dynamic_cast<TagLib::ASF::File *>(file)) return "ASF";
    return "unknown";
}

std::string id3v2Label(TagLib::ID3v2::Tag *tag) {
    std::string label = "ID3v2";
    if (tag != nullptr && tag->header() != nullptr) {
        label += "." + std::to_string(tag->header()->majorVersion());
        label += "." + std::to_string(tag->header()->revisionNumber());
    }
    return label;
}

/**
 * What kind of tag a file carries, and the concrete tag object when one is needed.
 *
 * File::tag() returns a TagUnion proxy for MPEG and FLAC - the object really is a
 * TagUnion, not the underlying tag - so casting it always fails. Those two formats
 * expose their real tags through format-specific accessors. MP4, ASF and Ogg/Vorbis
 * override tag() covariantly and hand back the concrete type directly.
 */
struct TagContext {
    std::string format = "unknown";
    TagLib::ID3v2::Tag *id3v2 = nullptr;
    TagLib::MP4::Tag *mp4 = nullptr;
};

TagContext classify(File *file) {
    TagContext context;

    if (auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file)) {
        if (mpeg->hasID3v2Tag()) {
            context.id3v2 = mpeg->ID3v2Tag(false);
            context.format = id3v2Label(context.id3v2);
        } else if (mpeg->hasAPETag()) {
            context.format = "APEv2";
        } else if (mpeg->hasID3v1Tag()) {
            context.format = "ID3v1";
        } else {
            context.format = "none";
        }
        if (mpeg->hasID3v1Tag() && context.id3v2 != nullptr) {
            context.format += "+ID3v1";
        }
        return context;
    }

    if (auto *flac = dynamic_cast<TagLib::FLAC::File *>(file)) {
        if (flac->hasID3v2Tag()) {
            context.id3v2 = flac->ID3v2Tag(false);
            context.format = id3v2Label(context.id3v2);
        } else if (flac->xiphComment(false) != nullptr) {
            context.format = "VorbisComment";
        } else {
            context.format = "none";
        }
        return context;
    }

    if (auto *vorbis = dynamic_cast<TagLib::Ogg::Vorbis::File *>(file)) {
        context.format = vorbis->tag() != nullptr ? "VorbisComment" : "none";
        return context;
    }

    if (auto *mp4 = dynamic_cast<TagLib::MP4::File *>(file)) {
        context.mp4 = mp4->tag();
        context.format = context.mp4 != nullptr ? "MP4" : "none";
        return context;
    }

    if (auto *asf = dynamic_cast<TagLib::ASF::File *>(file)) {
        context.format = asf->tag() != nullptr ? "ASF" : "none";
        return context;
    }

    return context;
}

// Bit depth is not part of the AudioProperties base class; only some formats expose it.
int bitsPerSampleOf(const AudioProperties *audio) {
    if (audio == nullptr) return 0;
    if (auto *flac = dynamic_cast<const TagLib::FLAC::Properties *>(audio)) return flac->bitsPerSample();
    if (auto *ape = dynamic_cast<const TagLib::APE::Properties *>(audio)) return ape->bitsPerSample();
    if (auto *asf = dynamic_cast<const TagLib::ASF::Properties *>(audio)) return asf->bitsPerSample();
    return 0;
}

void collectPictures(File *file, const TagContext &context, std::vector<Picture> &out) {
    if (auto *flac = dynamic_cast<TagLib::FLAC::File *>(file)) {
        for (auto *picture : flac->pictureList()) {
            if (picture != nullptr) {
                out.push_back({utf8(picture->mimeType()), picture->data()});
            }
        }
        return;
    }
    if (context.id3v2 != nullptr) {
        for (auto *frame : context.id3v2->frameList("APIC")) {
            if (auto *apic = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame *>(frame)) {
                out.push_back({utf8(apic->mimeType()), apic->picture()});
            }
        }
        return;
    }
    if (context.mp4 != nullptr) {
        const auto &items = context.mp4->itemMap();
        const auto it = items.find("covr");
        if (it != items.end()) {
            for (const auto &art : it->second.toCoverArtList()) {
                const char *mime =
                    art.format() == TagLib::MP4::CoverArt::PNG ? "image/png" : "image/jpeg";
                out.push_back({mime, art.data()});
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Write path (M2)
// ---------------------------------------------------------------------------

void trimInPlace(std::string &value) {
    while (!value.empty() && (value.front() == ' ' || value.front() == '\t')) value.erase(value.begin());
    while (!value.empty() && (value.back() == ' ' || value.back() == '\t')) value.pop_back();
}

std::string jstringToUtf8(JNIEnv *env, jstring value) {
    const jsize length = env->GetStringLength(value);
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out;
    out.reserve(static_cast<size_t>(length));
    for (jsize i = 0; i < length; ++i) {
        unsigned int cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length) {
            const unsigned int low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            }
        }
        if (cp < 0x80) {
            out.push_back(static_cast<char>(cp));
        } else if (cp < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

// ID3v2 keeps "3/12" in TRACKNUMBER; the model carries both the number and the combined
TagLib::MP4::CoverArt::Format coverFormatFor(const std::string &mime) {
    return mime == "image/png" ? TagLib::MP4::CoverArt::PNG : TagLib::MP4::CoverArt::JPEG;
}

/**
 * Applies a cover edit: leave it alone, remove it, or replace it.
 *
 * Each container stores artwork differently, so this dispatches per format:
 * FLAC keeps PICTURE blocks, MPEG keeps ID3v2 APIC frames, MP4 keeps a "covr" item.
 * APE cover art arrives in M3 together with the remaining formats.
 */
void applyCover(File *file, const TagContext &context, bool given, const ByteVector &data,
                const std::string &mime) {
    if (!given) {
        return;
    }

    if (auto *flac = dynamic_cast<TagLib::FLAC::File *>(file)) {
        flac->removePictures();
        if (!data.isEmpty()) {
            auto *picture = new TagLib::FLAC::Picture();
            picture->setType(TagLib::FLAC::Picture::FrontCover);
            picture->setMimeType(String(mime, String::UTF8));
            picture->setData(data);
            flac->addPicture(picture);  // FLAC::File takes ownership
        }
        return;
    }

    if (auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file)) {
        auto *tag = mpeg->ID3v2Tag(true);
        if (tag == nullptr) {
            return;
        }
        tag->removeFrames("APIC");
        if (!data.isEmpty()) {
            auto *frame = new TagLib::ID3v2::AttachedPictureFrame();
            frame->setMimeType(String(mime, String::UTF8));
            frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
            frame->setDescription(String());
            frame->setPicture(data);
            tag->addFrame(frame);
        }
        return;
    }

    if (context.mp4 != nullptr) {
        if (data.isEmpty()) {
            context.mp4->removeItem("covr");
        } else {
            TagLib::MP4::CoverArtList list;
            list.append(TagLib::MP4::CoverArt(coverFormatFor(mime), data));
            context.mp4->setItem("covr", TagLib::MP4::Item(list));
        }
    }
}


std::pair<std::string, std::string> splitPair(const std::string &number,
                                              const std::string &combined) {
    std::string value = number;
    std::string total;
    const std::string source = combined.empty() ? number : combined;
    const auto slash = source.find('/');
    if (slash != std::string::npos) {
        if (value.empty()) value = source.substr(0, slash);
        total = source.substr(slash + 1);
    }
    trimInPlace(value);
    trimInPlace(total);
    return {value, total};
}

std::string joinPair(const std::string &number, const std::string &total) {
    if (number.empty()) return total;
    return total.empty() ? number : number + "/" + total;
}

void putXiphText(TagLib::Ogg::XiphComment *comment, const char *key, const std::string &value) {
    if (comment == nullptr) return;
    if (value.empty()) {
        comment->removeFields(String(key, String::UTF8));
    } else {
        comment->addField(String(key, String::UTF8), String(value, String::UTF8), true);
    }
}

// One frame id, one value: removeFrames() first so repeated writes cannot accumulate
// (TagLib joins two TIT2 frames into "old / new" on read, which is not a replace).
void writeId3v2Text(TagLib::ID3v2::Tag *tag, const char *frameId, const std::string &value) {
    if (tag == nullptr) return;
    tag->removeFrames(ByteVector(frameId));
    if (value.empty()) return;
    auto *frame = new TagLib::ID3v2::TextIdentificationFrame(ByteVector(frameId), String::UTF8);
    frame->setText(String(value, String::UTF8));
    tag->addFrame(frame);
}

void writeId3v2Fields(File *file, const std::vector<std::string> &values, bool id3v24) {
    auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file);
    if (mpeg == nullptr) return;
    auto *tag = mpeg->ID3v2Tag(true);
    const auto track = splitPair(values[6], values[7]);
    const auto disc = splitPair(values[8], values[9]);

    writeId3v2Text(tag, "TIT2", values[0]);
    writeId3v2Text(tag, "TPE1", values[1]);
    writeId3v2Text(tag, "TALB", values[2]);
    writeId3v2Text(tag, "TPE2", values[3]);
    writeId3v2Text(tag, "TCON", values[4]);
    writeId3v2Text(tag, "TCOM", values[5]);
    writeId3v2Text(tag, "TRCK", joinPair(track.first, track.second));
    writeId3v2Text(tag, "TPOS", joinPair(disc.first, disc.second));

    writeId3v2Text(tag, id3v24 ? "TDRC" : "TYER", values[10]);
    // Never keep both year frames: that is what a v2.3/v2.4 conversion would leave behind.
    tag->removeFrames(ByteVector(id3v24 ? "TYER" : "TDRC"));

    tag->removeFrames("COMM");
    if (!values[11].empty()) {
        auto *comment = new TagLib::ID3v2::CommentsFrame(String::UTF8);
        comment->setLanguage(ByteVector("eng"));
        comment->setText(String(values[11], String::UTF8));
        tag->addFrame(comment);
    }

    tag->removeFrames("USLT");
    if (!values[12].empty()) {
        auto *lyrics = new TagLib::ID3v2::UnsynchronizedLyricsFrame(String::UTF8);
        lyrics->setLanguage(ByteVector("eng"));
        lyrics->setText(String(values[12], String::UTF8));
        tag->addFrame(lyrics);
    }
}

void writeFlacFields(File *file, const std::vector<std::string> &values) {
    auto *flac = dynamic_cast<TagLib::FLAC::File *>(file);
    if (flac == nullptr) return;
    auto *comment = flac->xiphComment(true);
    const auto track = splitPair(values[6], values[7]);
    const auto disc = splitPair(values[8], values[9]);

    putXiphText(comment, "TITLE", values[0]);
    putXiphText(comment, "ARTIST", values[1]);
    putXiphText(comment, "ALBUM", values[2]);
    putXiphText(comment, "ALBUMARTIST", values[3]);
    putXiphText(comment, "GENRE", values[4]);
    putXiphText(comment, "COMPOSER", values[5]);
    putXiphText(comment, "TRACKNUMBER", track.first);
    putXiphText(comment, "TRACKTOTAL", track.second);
    putXiphText(comment, "DISCNUMBER", disc.first);
    putXiphText(comment, "DISCTOTAL", disc.second);
    putXiphText(comment, "DATE", values[10]);
    putXiphText(comment, "COMMENT", values[11]);
    putXiphText(comment, "LYRICS", values[12]);
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_xjcheng_musictageditor_core_tag_NativeTagBridge_taglibVersion(JNIEnv *env, jclass) {
    const std::string version = std::to_string(TAGLIB_MAJOR_VERSION) + "." +
                                std::to_string(TAGLIB_MINOR_VERSION) + "." +
                                std::to_string(TAGLIB_PATCH_VERSION);
    return toJavaString(env, version);
}

// Returns null when the file cannot be opened as audio. Layout (see TagData):
//   0 container, 1 tagFormat,
//   2..13 title, artist, album, albumArtist, genre, composer,
//         track, trackStr, disc, discStr, year, comment,
//   14 lyrics,
//   15 durationMs, 16 bitrateKbps, 17 sampleRateHz, 18 channels, 19 bitsPerSample,
//   20 pictureCount,
//   21.. per picture: mime, base64 data
JNIEXPORT jobjectArray JNICALL
Java_com_xjcheng_musictageditor_core_tag_NativeTagBridge_readAll(JNIEnv *env, jclass, jstring path) {
    if (path == nullptr) {
        return nullptr;
    }
    const char *rawPath = env->GetStringUTFChars(path, nullptr);
    if (rawPath == nullptr) {
        return nullptr;
    }
    const std::string filePath(rawPath);
    env->ReleaseStringUTFChars(path, rawPath);

    FileRef ref(filePath.c_str(), true);
    if (ref.isNull() || ref.file() == nullptr) {
        return nullptr;
    }
    File *file = ref.file();
    Tag *tag = ref.tag();  // TagUnion proxy: forwards every Tag accessor
    const TagContext context = classify(file);
    const PropertyMap properties = file->properties();

    std::vector<std::string> fields;
    fields.reserve(32);

    fields.push_back(detectContainer(file));
    fields.push_back(context.format);

    fields.push_back(tag != nullptr ? utf8(tag->title()) : "");
    fields.push_back(tag != nullptr ? utf8(tag->artist()) : "");
    fields.push_back(tag != nullptr ? utf8(tag->album()) : "");

    const std::string albumArtist = firstProperty(properties, "ALBUMARTIST");
    fields.push_back(!albumArtist.empty() ? albumArtist : firstProperty(properties, "ALBUM ARTIST"));
    fields.push_back(tag != nullptr ? utf8(tag->genre()) : "");
    fields.push_back(firstProperty(properties, "COMPOSER"));

    // ID3v2 exposes "3/12" verbatim in TRACKNUMBER while VorbisComment splits it into
    // TRACKNUMBER/TRACKTOTAL, so the combined form has to be split here.
    const auto splitNumber = [](const std::string &raw, const std::string &explicitTotal) {
        std::string number = raw;
        std::string total = explicitTotal;
        const auto slash = raw.find('/');
        if (slash != std::string::npos) {
            number = raw.substr(0, slash);
            if (total.empty()) {
                total = raw.substr(slash + 1);
            }
        }
        while (!number.empty() && (number.back() == ' ' || number.back() == '\t')) number.pop_back();
        while (!total.empty() && (total.back() == ' ' || total.back() == '\t')) total.pop_back();
        return std::make_pair(number, total);
    };
    const auto joinWithTotal = [](const std::string &number, const std::string &total) {
        if (number.empty()) return total.empty() ? std::string() : total;
        return total.empty() ? number : number + "/" + total;
    };

    std::string trackRaw = firstProperty(properties, "TRACKNUMBER");
    if (trackRaw.empty() && tag != nullptr && tag->track() > 0) {
        trackRaw = std::to_string(tag->track());
    }
    const auto track = splitNumber(trackRaw, firstProperty(properties, "TRACKTOTAL"));
    const auto disc = splitNumber(firstProperty(properties, "DISCNUMBER"),
                                  firstProperty(properties, "DISCTOTAL"));

    fields.push_back(track.first);
    fields.push_back(joinWithTotal(track.first, track.second));
    fields.push_back(disc.first);
    fields.push_back(joinWithTotal(disc.first, disc.second));

    std::string year = firstProperty(properties, "DATE");
    if (year.size() > 4) {
        year = year.substr(0, 4);
    }
    if (year.empty() && tag != nullptr && tag->year() > 0) {
        year = std::to_string(tag->year());
    }
    fields.push_back(year);
    fields.push_back(tag != nullptr ? utf8(tag->comment()) : "");

    std::string lyrics = firstProperty(properties, "LYRICS");
    if (lyrics.empty()) {
        lyrics = firstProperty(properties, "UNSYNCEDLYRICS");
    }
    fields.push_back(lyrics);

    const AudioProperties *audio = ref.audioProperties();
    fields.push_back(audio != nullptr ? std::to_string(audio->lengthInMilliseconds()) : "");
    fields.push_back(audio != nullptr ? std::to_string(audio->bitrate()) : "");
    fields.push_back(audio != nullptr ? std::to_string(audio->sampleRate()) : "");
    fields.push_back(audio != nullptr ? std::to_string(audio->channels()) : "");
    fields.push_back(std::to_string(bitsPerSampleOf(audio)));

    std::vector<Picture> pictures;
    collectPictures(file, context, pictures);
    fields.push_back(std::to_string(pictures.size()));
    for (const auto &picture : pictures) {
        fields.push_back(picture.mime);
        fields.push_back(base64(picture.data));
    }

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(fields.size()), stringClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    for (size_t i = 0; i < fields.size(); ++i) {
        jstring value = toJavaString(env, fields[i]);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

// Writes the twelve tag fields plus lyrics and an optional cover edit.
//
//   fields  : exactly 13 UTF-8 values -
//             0 title, 1 artist, 2 album, 3 albumArtist, 4 genre, 5 composer,
//             6 track, 7 trackStr, 8 disc, 9 discStr, 10 year, 11 comment, 12 lyrics
//   cover   : null = leave unchanged, empty = remove, otherwise = replace
//   options : bit 0 write ID3v1 as well, bit 1 write ID3v2.3 instead of ID3v2.4
//
// Returns null on success, otherwise a human readable reason.
JNIEXPORT jstring JNICALL
Java_com_xjcheng_musictageditor_core_tag_NativeTagBridge_writeAll(
    JNIEnv *env, jclass, jstring path, jobjectArray fields, jbyteArray cover,
    jstring coverMime, jint options) {
    if (path == nullptr || fields == nullptr) {
        return toJavaString(env, "missing path or fields");
    }
    if (env->GetArrayLength(fields) < 13) {
        return toJavaString(env, "expected 13 fields");
    }

    const char *rawPath = env->GetStringUTFChars(path, nullptr);
    if (rawPath == nullptr) {
        return toJavaString(env, "path conversion failed");
    }
    const std::string filePath(rawPath);
    env->ReleaseStringUTFChars(path, rawPath);

    std::vector<std::string> values(13);
    for (jsize i = 0; i < 13; ++i) {
        auto *element = static_cast<jstring>(env->GetObjectArrayElement(fields, i));
        if (element != nullptr) {
            values[static_cast<size_t>(i)] = jstringToUtf8(env, element);
            env->DeleteLocalRef(element);
        }
    }

    FileRef ref(filePath.c_str(), false);
    if (ref.isNull() || ref.file() == nullptr) {
        return toJavaString(env, "unsupported container");
    }
    File *file = ref.file();
    const TagContext context = classify(file);

    const bool isMpeg = dynamic_cast<TagLib::MPEG::File *>(file) != nullptr;
    if (isMpeg) {
        writeId3v2Fields(file, values, (options & 2) == 0);
    } else if (dynamic_cast<TagLib::FLAC::File *>(file) != nullptr) {
        writeFlacFields(file, values);
    } else {
        return toJavaString(env, "writing is not supported for this container yet");
    }

    bool coverGiven = cover != nullptr;
    ByteVector coverData;
    if (coverGiven) {
        const jsize size = env->GetArrayLength(cover);
        if (size > 0) {
            jbyte *bytes = env->GetByteArrayElements(cover, nullptr);
            if (bytes != nullptr) {
                coverData = ByteVector(reinterpret_cast<const char *>(bytes),
                                       static_cast<unsigned int>(size));
                env->ReleaseByteArrayElements(cover, bytes, JNI_ABORT);
            }
        }
    }
    std::string coverType;
    if (coverMime != nullptr) {
        const char *rawMime = env->GetStringUTFChars(coverMime, nullptr);
        if (rawMime != nullptr) {
            coverType = rawMime;
            env->ReleaseStringUTFChars(coverMime, rawMime);
        }
    }
    applyCover(file, context, coverGiven, coverData, coverType);

    bool saved = false;
    if (auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file)) {
        const bool wantV1 = (options & 1) != 0;
        const bool hasV1 = mpeg->hasID3v1Tag();
        int tags = TagLib::MPEG::File::ID3v2;
        if (wantV1 || hasV1) {
            tags |= TagLib::MPEG::File::ID3v1;
            // Refresh ID3v1 from ID3v2 with overwrite=true. Letting save() merge both
            // directions appends the old values and produces "a / b" titles.
            TagLib::Tag::duplicate(mpeg->ID3v2Tag(false), mpeg->ID3v1Tag(true), true);
        }
        if (mpeg->hasAPETag()) {
            tags |= TagLib::MPEG::File::APE;  // keep existing APE tags instead of stripping
        }
        const TagLib::ID3v2::Version version =
            (options & 2) != 0 ? TagLib::ID3v2::v3 : TagLib::ID3v2::v4;
        saved = mpeg->save(tags, TagLib::File::StripOthers, version,
                           TagLib::File::DoNotDuplicate);
    } else {
        saved = file->save();
    }

    if (!saved) {
        return toJavaString(env, "save failed (read-only file or unsupported tag)");
    }
    return nullptr;
}
}  // extern "C"
