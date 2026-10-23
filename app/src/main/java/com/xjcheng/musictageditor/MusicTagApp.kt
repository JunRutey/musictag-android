/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.app.Application

/**
 * Application entry point.
 *
 * Deliberately does no work in M0: no global mutable state, no eager I/O.
 * Long-lived collaborators (settings repository, media index, provider registry)
 * are created lazily by the modules that own them.
 */
class MusicTagApp : Application()
