/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "HTTPLiveSource"
#include <utils/Log.h>

#include "HTTPLiveSource.h"

#include "AnotherPacketSource.h"
#include "LiveDataSource.h"
#include "LiveSession.h"

#include <media/IMediaHTTPService.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>

namespace android {

NuPlayer::HTTPLiveSource::HTTPLiveSource(
        const sp<AMessage> &notify,
        const sp<IMediaHTTPService> &httpService,
        const char *url,
        const KeyedVector<String8, String8> *headers)
    : Source(notify),
      mHTTPService(httpService),
      mURL(url),
      mFlags(0),
      mFinalResult(OK),
      mOffset(0),
      mFetchSubtitleDataGeneration(0) {
    if (headers) {
        mExtraHeaders = *headers;

        ssize_t index =
            mExtraHeaders.indexOfKey(String8("x-hide-urls-from-log"));

        if (index >= 0) {
            mFlags |= kFlagIncognito;

            mExtraHeaders.removeItemsAt(index);
        }
    }
}

NuPlayer::HTTPLiveSource::~HTTPLiveSource() {
    if (mLiveSession != NULL) {
        mLiveSession->disconnect();

        mLiveLooper->unregisterHandler(mLiveSession->id());
        mLiveLooper->unregisterHandler(id());
        mLiveLooper->stop();

        mLiveSession.clear();
        mLiveLooper.clear();
    }
}

void NuPlayer::HTTPLiveSource::prepareAsync() {
    if (mLiveLooper == NULL) {
        mLiveLooper = new ALooper;
        mLiveLooper->setName("http live");
        mLiveLooper->start();

        mLiveLooper->registerHandler(this);
    }

    sp<AMessage> notify = new AMessage(kWhatSessionNotify, id());

    mLiveSession = new LiveSession(
            notify,
            (mFlags & kFlagIncognito) ? LiveSession::kFlagIncognito : 0,
            mHTTPService);

    mLiveLooper->registerHandler(mLiveSession);

    mLiveSession->connectAsync(
            mURL.c_str(), mExtraHeaders.isEmpty() ? NULL : &mExtraHeaders);
}

void NuPlayer::HTTPLiveSource::start() {
}

sp<AMessage> NuPlayer::HTTPLiveSource::getFormat(bool audio) {
    sp<AMessage> format;
    status_t err = mLiveSession->getStreamFormat(
            audio ? LiveSession::STREAMTYPE_AUDIO
                  : LiveSession::STREAMTYPE_VIDEO,
            &format);

    if (err != OK) {
        return NULL;
    }

    return format;
}

status_t NuPlayer::HTTPLiveSource::feedMoreTSData() {
    return OK;
}

status_t NuPlayer::HTTPLiveSource::dequeueAccessUnit(
        bool audio, sp<ABuffer> *accessUnit) {
    return mLiveSession->dequeueAccessUnit(
            audio ? LiveSession::STREAMTYPE_AUDIO
                  : LiveSession::STREAMTYPE_VIDEO,
            accessUnit);
}

status_t NuPlayer::HTTPLiveSource::getDuration(int64_t *durationUs) {
    return mLiveSession->getDuration(durationUs);
}

size_t NuPlayer::HTTPLiveSource::getTrackCount() const {
    return mLiveSession->getTrackCount();
}

sp<AMessage> NuPlayer::HTTPLiveSource::getTrackInfo(size_t trackIndex) const {
    return mLiveSession->getTrackInfo(trackIndex);
}

status_t NuPlayer::HTTPLiveSource::selectTrack(size_t trackIndex, bool select) {
    status_t err = mLiveSession->selectTrack(trackIndex, select);

    if (err == OK) {
        mFetchSubtitleDataGeneration++;
        if (select) {
            sp<AMessage> msg = new AMessage(kWhatFetchSubtitleData, id());
            msg->setInt32("generation", mFetchSubtitleDataGeneration);
            msg->post();
        }
    }

    // LiveSession::selectTrack returns BAD_VALUE when selecting the currently
    // selected track, or unselecting a non-selected track. In this case it's an
    // no-op so we return OK.
    return (err == OK || err == BAD_VALUE) ? (status_t)OK : err;
}

status_t NuPlayer::HTTPLiveSource::seekTo(int64_t seekTimeUs) {
    return mLiveSession->seekTo(seekTimeUs);
}

void NuPlayer::HTTPLiveSource::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatSessionNotify:
        {
            onSessionNotify(msg);
            break;
        }

        case kWhatFetchSubtitleData:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));

            if (generation != mFetchSubtitleDataGeneration) {
                // stale
                break;
            }

            sp<ABuffer> buffer;
            if (mLiveSession->dequeueAccessUnit(
                    LiveSession::STREAMTYPE_SUBTITLES, &buffer) == OK) {
                sp<AMessage> notify = dupNotify();
                notify->setInt32("what", kWhatSubtitleData);
                notify->setBuffer("buffer", buffer);
                notify->post();

                int64_t timeUs, baseUs, durationUs, delayUs;
                CHECK(buffer->meta()->findInt64("baseUs", &baseUs));
                CHECK(buffer->meta()->findInt64("timeUs", &timeUs));
                CHECK(buffer->meta()->findInt64("durationUs", &durationUs));
                delayUs = baseUs + timeUs - ALooper::GetNowUs();

                msg->post(delayUs > 0ll ? delayUs : 0ll);
            } else {
                // try again in 1 second
                msg->post(1000000ll);
            }

            break;
        }

        default:
            Source::onMessageReceived(msg);
            break;
    }
}

void NuPlayer::HTTPLiveSource::onSessionNotify(const sp<AMessage> &msg) {
    int32_t what;
    CHECK(msg->findInt32("what", &what));

    switch (what) {
        case LiveSession::kWhatPrepared:
        {
            // notify the current size here if we have it, otherwise report an initial size of (0,0)
            sp<AMessage> format = getFormat(false /* audio */);
            int32_t width;
            int32_t height;
            if (format != NULL &&
                    format->findInt32("width", &width) && format->findInt32("height", &height)) {
                notifyVideoSizeChanged(format);
            } else {
                notifyVideoSizeChanged();
            }

#ifdef MTK_AOSP_ENHANCEMENT
            uint32_t flags = 0;
#else            
            uint32_t flags = FLAG_CAN_PAUSE;
#endif
            if (mLiveSession->isSeekable()) {
#ifdef MTK_AOSP_ENHANCEMENT
                flags |= FLAG_CAN_PAUSE;
#endif                
                flags |= FLAG_CAN_SEEK;
                flags |= FLAG_CAN_SEEK_BACKWARD;
                flags |= FLAG_CAN_SEEK_FORWARD;
            }

            if (mLiveSession->hasDynamicDuration()) {
                flags |= FLAG_DYNAMIC_DURATION;
            }

            notifyFlagsChanged(flags);

            notifyPrepared();
            break;
        }

        case LiveSession::kWhatPreparationFailed:
        {
            status_t err;
            CHECK(msg->findInt32("err", &err));

            notifyPrepared(err);
            break;
        }

        case LiveSession::kWhatStreamsChanged:
        {
            uint32_t changedMask;
            CHECK(msg->findInt32(
                        "changedMask", (int32_t *)&changedMask));

            bool audio = changedMask & LiveSession::STREAMTYPE_AUDIO;
            bool video = changedMask & LiveSession::STREAMTYPE_VIDEO;
#ifdef MTK_AOSP_ENHANCEMENT
			ALOGI("receive LiveSession::kWhatStreamsChanged,queue Decoder Shutdown for %s,%s",\
				audio?"audio":"",video?"video":"");
#endif
            sp<AMessage> reply;
            CHECK(msg->findMessage("reply", &reply));

            sp<AMessage> notify = dupNotify();
            notify->setInt32("what", kWhatQueueDecoderShutdown);
            notify->setInt32("audio", audio);
            notify->setInt32("video", video);
            notify->setMessage("reply", reply);
            notify->post();
            break;
        }

        case LiveSession::kWhatError:
        {
            break;
        }
#ifdef MTK_AOSP_ENHANCEMENT
        case LiveSession::kWhatPicture:
        case LiveSession::kWhatBufferingStart:
        case LiveSession::kWhatBufferingEnd:
        {
            onSessionNotify_l(msg);
            break;
        }
#endif
        default:
            TRESPASS();
    }
}

#ifdef MTK_AOSP_ENHANCEMENT
status_t NuPlayer::HTTPLiveSource::allTracksPresent() {	
    bool tracksPresent = mLiveSession->allTracksPresent();
  	
    return tracksPresent ? OK : -EWOULDBLOCK;
}

void NuPlayer::HTTPLiveSource::onSessionNotify_l(const sp<AMessage> &msg) {
    int32_t what;
    CHECK(msg->findInt32("what", &what));

    if(what == LiveSession::kWhatPicture) {
        sp<ABuffer> metabuffer;
        CHECK(msg->findBuffer("buffer", &metabuffer));
        
        AString mimeType;
        sp<ABuffer> buffer;
        if(((metabuffer)->meta()->findString("mime", &mimeType)) &&
                ((metabuffer)->meta()->findBuffer("pictureBuffer", &buffer))) {
            if (mMetaData == NULL) {
                mMetaData = new MetaData;
            }
            mMetaData->setCString(kKeyAlbumArtMIME, mimeType.c_str());
            mMetaData->setData(kKeyAlbumArt, MetaData::TYPE_NONE, buffer->data(), buffer->size());
            ALOGI("kKeyAlbumArt set Data :%s, datasize:%d", mimeType.c_str(), buffer->size());
            
            sp<AMessage> notify = dupNotify();
            notify->setInt32("what", NuPlayer::Source::kWhatPicture);
            notify->post();
        }
    }
    else if (what == LiveSession::kWhatBufferingStart) {
        sp<AMessage> notify = dupNotify();
        notify->setInt32("what", kWhatBufferingStart);
        notify->post();
    }
    else if (what == LiveSession::kWhatBufferingEnd) {
        sp<AMessage> notify = dupNotify();
        notify->setInt32("what", kWhatBufferingEnd);
        notify->post();
    }
}

#endif    
}  // namespace android
