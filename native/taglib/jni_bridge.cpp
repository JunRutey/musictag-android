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
#include <mpeg/id3v2/id3v2tag.h>
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

std::string detectTagFormat(File *file, Tag *tag) {
    if (auto *id3v2 = dynamic_cast<TagLib::ID3v2::Tag *>(tag)) {
        std::string version = "ID3v2";
        if (id3v2->header()) {
            version += "." + std::to_string(id3v2->header()->majorVersion());
            version += "." + std::to_string(id3v2->header()->revisionNumber());
        }
        auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file);
        if (mpeg != nullptr && mpeg->hasID3v1Tag()) {
            version += "+ID3v1";
        }
        return version;
    }
    if (dynamic_cast<TagLib::XiphComment *>(tag)) return "VorbisComment";
    if (dynamic_cast<TagLib::MP4::Tag *>(tag)) return "MP4";
    if (dynamic_cast<TagLib::APE::Tag *>(tag)) return "APEv2";
    if (dynamic_cast<TagLib::ASF::Tag *>(tag)) return "ASF";
    return "unknown";
}

// Bit depth is not part of the AudioProperties base class; only some formats expose it.
int bitsPerSampleOf(const AudioProperties *audio) {
    if (audio == nullptr) return 0;
    if (auto *flac = dynamic_cast<const TagLib::FLAC::Properties *>(audio)) return flac->bitsPerSample();
    if (auto *ape = dynamic_cast<const TagLib::APE::Properties *>(audio)) return ape->bitsPerSample();
    if (auto *asf = dynamic_cast<const TagLib::ASF::Properties *>(audio)) return asf->bitsPerSample();
    return 0;
}

void collectPictures(File *file, Tag *tag, std::vector<Picture> &out) {
    if (auto *flac = dynamic_cast<TagLib::FLAC::File *>(file)) {
        for (auto *picture : flac->pictureList()) {
            if (picture != nullptr) {
                out.push_back({utf8(picture->mimeType()), picture->picture()});
            }
        }
        return;
    }
    if (auto *id3v2 = dynamic_cast<TagLib::ID3v2::Tag *>(tag)) {
        for (auto *frame : id3v2->frameList("APIC")) {
            if (auto *apic = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame *>(frame)) {
                out.push_back({utf8(apic->mimeType()), apic->picture()});
            }
        }
        return;
    }
    if (auto *mp4 = dynamic_cast<TagLib::MP4::Tag *>(tag)) {
        const auto &items = mp4->itemMap();
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
    Tag *tag = ref.tag();
    const PropertyMap properties = file->properties();

    std::vector<std::string> fields;
    fields.reserve(32);

    fields.push_back(detectContainer(file));
    fields.push_back(tag != nullptr ? detectTagFormat(file, tag) : "unknown");

    fields.push_back(tag != nullptr ? utf8(tag->title()) : "");
    fields.push_back(tag != nullptr ? utf8(tag->artist()) : "");
    fields.push_back(tag != nullptr ? utf8(tag->album()) : "");

    const std::string albumArtist = firstProperty(properties, "ALBUMARTIST");
    fields.push_back(!albumArtist.empty() ? albumArtist : firstProperty(properties, "ALBUM ARTIST"));
    fields.push_back(tag != nullptr ? utf8(tag->genre()) : "");
    fields.push_back(firstProperty(properties, "COMPOSER"));

    std::string trackNumber = firstProperty(properties, "TRACKNUMBER");
    if (trackNumber.empty() && tag != nullptr && tag->track() > 0) {
        trackNumber = std::to_string(tag->track());
    }
    const std::string trackTotal = firstProperty(properties, "TRACKTOTAL");
    const std::string discNumber = firstProperty(properties, "DISCNUMBER");
    const std::string discTotal = firstProperty(properties, "DISCTOTAL");
    const auto joinWithTotal = [](const std::string &number, const std::string &total) {
        if (number.empty()) return total.empty() ? std::string() : total;
        return total.empty() ? number : number + "/" + total;
    };

    fields.push_back(trackNumber);
    fields.push_back(joinWithTotal(trackNumber, trackTotal));
    fields.push_back(discNumber);
    fields.push_back(joinWithTotal(discNumber, discTotal));

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
    collectPictures(file, tag, pictures);
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
}  // extern "C"
