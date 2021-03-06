/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.rtp;

import org.atalk.impl.neomedia.MediaStreamImpl;
import org.atalk.impl.neomedia.RTPPacketPredicate;
import org.atalk.impl.neomedia.transform.PacketTransformer;
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.util.ArrayUtils;

/**
 * This class is inserted in the receive transform chain and it updates the
 * {@link MediaStreamTrackDesc}s that is configured to receive.
 *
 * @author George Politis
 */
public class MediaStreamTrackReceiver extends SinglePacketTransformerAdapter implements TransformEngine
{
    /**
     * An empty array of {@link MediaStreamTrackDesc}.
     */
    private static final MediaStreamTrackDesc[] NO_TRACKS = new MediaStreamTrackDesc[0];

    /**
     * The {@link MediaStreamImpl} that owns this instance.
     */
    private final MediaStreamImpl stream;

    /**
     * The {@link MediaStreamTrackDesc}s that this instance is configured to receive.
     */
    private MediaStreamTrackDesc[] tracks = NO_TRACKS;

    /**
     * Ctor.
     *
     * @param stream The {@link MediaStream} that this instance receives
     * {@link MediaStreamTrackDesc}s from.
     */
    public MediaStreamTrackReceiver(MediaStreamImpl stream)
    {
        super(RTPPacketPredicate.INSTANCE);
        this.stream = stream;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that matches the {@link RawPacket}
     * passed in as a parameter. Assumes that the packet is valid.
     *
     * @param pkt the packet to match.
     * @return the {@link RTPEncodingDesc} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncodingDesc}.
     */
    public RTPEncodingDesc findRTPEncodingDesc(RawPacket pkt)
    {
        MediaStreamTrackDesc[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks)) {
            return null;
        }

        for (MediaStreamTrackDesc track : localTracks) {
            RTPEncodingDesc encoding = track.findRTPEncodingDesc(pkt);
            if (encoding != null) {
                return encoding;
            }
        }
        return null;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that matches {@link ByteArrayBuffer}
     * passed in as a parameter.
     *
     * @param ssrc the SSRC of the {@link RTPEncodingDesc} to match. If multiple
     * encodings share the same SSRC, the first match will be returned.
     * @return the {@link RTPEncodingDesc} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncodingDesc}.
     */
    public RTPEncodingDesc findRTPEncodingDesc(long ssrc)
    {
        MediaStreamTrackDesc[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks)) {
            return null;
        }

        for (MediaStreamTrackDesc track : localTracks) {
            RTPEncodingDesc encoding = track.findRTPEncodingDesc(ssrc);
            if (encoding != null) {
                return encoding;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Gets the {@link MediaStreamTrackDesc}s that this instance is configured to receive.
     *
     * @return the {@link MediaStreamTrackDesc}s that this instance is configured to receive.
     */
    public MediaStreamTrackDesc[] getMediaStreamTracks()
    {
        return tracks == null ? NO_TRACKS : tracks;
    }

    /**
     * Updates this {@link MediaStreamTrackReceiver} with the new RTP encoding
     * parameters. Note that in order to avoid losing the state of existing
     * {@link MediaStreamTrackDesc} instances, when one of the new instances
     * matches (i.e. the primary SSRC of its first encoding matches) an old
     * instance we keep the old instance.
     * Currently we also keep the old instance's configuration
     * (TODO use the new configuration).
     *
     * @param newTracks the {@link MediaStreamTrackDesc}s that this instance
     * will receive. Note that the actual {@link MediaStreamTrackDesc} instances
     * might not match. To get the actual instances call
     * {@link #getMediaStreamTracks()}.
     * @return true if the MSTs have changed, otherwise false.
     */
    public boolean setMediaStreamTracks(MediaStreamTrackDesc[] newTracks)
    {
        if (newTracks == null) {
            newTracks = NO_TRACKS;
        }

        MediaStreamTrackDesc[] oldTracks = tracks;
        int oldTracksLen = oldTracks == null ? 0 : oldTracks.length;
        int newTracksLen = newTracks == null ? 0 : newTracks.length;

        if (oldTracksLen == 0 || newTracksLen == 0) {
            tracks = newTracks;
            return oldTracksLen != newTracksLen;
        }
        else {
            int cntMatched = 0;
            MediaStreamTrackDesc[] mergedTracks = new MediaStreamTrackDesc[newTracks.length];

            for (int i = 0; i < newTracks.length; i++) {
                RTPEncodingDesc newEncoding = newTracks[i].getRTPEncodings()[0];

                for (MediaStreamTrackDesc oldTrack : oldTracks) {
                    if (oldTrack != null && oldTrack.matches(newEncoding.getPrimarySSRC())) {
                        mergedTracks[i] = oldTrack;
                        cntMatched++;
                        break;
                    }
                }
                if (mergedTracks[i] == null) {
                    mergedTracks[i] = newTracks[i];
                }
            }
            tracks = mergedTracks;
            return oldTracksLen != newTracksLen || cntMatched != oldTracks.length;
        }
    }

    /**
     * Gets the {@code RtpChannel} that owns this instance.
     *
     * @return the {@code RtpChannel} that owns this instance.
     */
    public MediaStreamImpl getStream()
    {
        return stream;
    }

    /**
     * Finds the {@link MediaStreamTrackDesc} that corresponds to the SSRC that
     * is specified in the arguments.
     *
     * @param ssrc the SSRC of the {@link MediaStreamTrackDesc} to match.
     * @return the {@link MediaStreamTrackDesc} that matches the specified SSRC.
     */
    public MediaStreamTrackDesc findMediaStreamTrackDesc(long ssrc)
    {
        MediaStreamTrackDesc[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks)) {
            return null;
        }

        for (MediaStreamTrackDesc track : localTracks) {
            if (track.matches(ssrc)) {
                return track;
            }
        }
        return null;
    }
}
