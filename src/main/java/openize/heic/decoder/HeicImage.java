/*
 * Openize.HEIC
 * Copyright (c) 2024-2025 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.decoder;

import openize.heic.decoder.io.BitStreamWithNalSupport;
import openize.io.IOStream;
import openize.isobmff.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * <p>
 * Heic image class.
 * </p>
 */
public class HeicImage
{
    /**
     * <p>
     * Dictionary of Heic image frames.
     * </p>
     */
    private final HashMap<Long, HeicImageFrame> _frames;
    private final HeicHeader header;

    /**
     * <p>
     * Create heic image object.
     * </p>
     *
     * @param header File header.
     * @param stream File stream.
     */
    private HeicImage(HeicHeader header, BitStreamWithNalSupport stream)
    {
        this.header = header;
        _frames = new HashMap<>();
        readFramesMeta(stream);
    }

    /**
     * <p>
     * Reads the file meta data and creates a class object for further decoding of the file contents.
     * <p>This operation does not decode pixels.
     * Use the default frame methods GetByteArray or GetInt32Array afterwards in order to decode pixels.</p>
     * </p>
     *
     * @param stream File stream.
     * @return Returns a heic image object with metadata read.
     */
    public static HeicImage load(IOStream stream)
    {
        BitStreamWithNalSupport bitstream = new BitStreamWithNalSupport(stream, 4);
        bitstream.setBytePosition(0);

        /*UInt64*/
        Box.setExternalConstructor(BoxType.hvcC, (str, size) -> {
            if (!(str instanceof BitStreamWithNalSupport))
            {
                throw new UnsupportedOperationException("Stream dedication logic error.");
            }
            BitStreamWithNalSupport nalStream = (BitStreamWithNalSupport) str;
            return new HEVCConfigurationBox(nalStream, size);
        });

        while (bitstream.moreData())
        {
            Box box = Box.parceBox(bitstream);

            if (box.type == BoxType.meta)
            {
                return new HeicImage(new HeicHeader((MetaBox) box), bitstream);
            }
        }

        throw new UnsupportedOperationException("Meta box not found.");
    }

    /**
     * <p>
     * Checks if the stream can be read as a heic image.
     * </p>
     *
     * @param stream File stream.
     * @return True if file header contains HEIC signature, false otherwise.
     */
    public static boolean canLoad(IOStream stream)
    {
        BitStreamWithNalSupport bitstream = new BitStreamWithNalSupport(stream);

        Box box = Box.parceBox(bitstream);

        if (!(box instanceof FileTypeBox))
        {
            return false;
        }

        FileTypeBox filetype = (FileTypeBox) box;
        if (!filetype.isBrandSupported(1751476579)) // heic (ASCII)
        {
            return false;
        }

        bitstream.setBytePosition(0);

        return true;
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of byte array.
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     * </p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @return Byte array, null if frame does not contain image data.
     */
    public final /*Byte*/byte[] getByteArray(PixelFormat pixelFormat)
    {
        return getByteArray(pixelFormat, new Rectangle());
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of byte array.
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     * </p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @param boundsRectangle Bounds of the requested area.
     * @return Byte array, null if frame does not contain image data.
     */
    public final /*Byte*/byte[] getByteArray(PixelFormat pixelFormat, Rectangle boundsRectangle)
    {
        return getDefaultFrame().getByteArray(pixelFormat, boundsRectangle);
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of integer array.
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     * </p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @return Integer array, null if frame does not contain image data.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat)
    {
        return getInt32Array(pixelFormat, new Rectangle());
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of integer array.
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     * </p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @param boundsRectangle Bounds of the requested area.
     * @return Integer array, null if frame does not contain image data.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat, Rectangle boundsRectangle)
    {
        return getDefaultFrame().getInt32Array(pixelFormat, boundsRectangle);
    }

    /**
     * <p>
     * Heic image header. Grants convenient access to IsoBmff container meta data.
     * </p>
     */
    public final HeicHeader getHeader()
    {
        return header;
    }

    /**
     * <p>
     * Dictionary of public Heic image frames with access by identifier.
     * </p>
     */
    public final Map<Long, HeicImageFrame> getFrames()
    {
        return _frames.entrySet().stream().filter((f) -> !f.getValue().isHidden())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * <p>
     * Dictionary of all Heic image frames with access by identifier.
     * </p>
     */
    public final Map<Long, HeicImageFrame> getAllFrames()
    {
        return _frames;
    }

    /**
     * <p>
     * Returns the default image frame, which is specified in meta data.
     * </p>
     */
    public final HeicImageFrame getDefaultFrame()
    {
        return _frames.get(getHeader().getDefaultFrameId());
    }

    /**
     * <p>
     * Width of the default image frame in pixels.
     * </p>
     */
    public final /*UInt32*/long getWidth()
    {
        return getDefaultFrame().getWidth();
    }

    /**
     * <p>
     * Height of the default image frame in pixels.
     * </p>
     */
    public final /*UInt32*/long getHeight()
    {
        return getDefaultFrame().getHeight();
    }

    /**
     * <p>
     * Fill frames dictionary with read meta data.
     * </p>
     *
     * @param stream File stream.
     */
    private void readFramesMeta(BitStreamWithNalSupport stream)
    {
        Map<Long, List<Box>> rawProperties = getHeader().getProperties();

        for (IlocItem item : getHeader().getMeta().getiloc().items)
        {
            /*UInt32*/
            long id = item.item_ID;

            final List<Box> properties = rawProperties.getOrDefault(id, new ArrayList<>());
            _frames.put(id, new HeicImageFrame(stream, this, id, properties));
        }
    }
}
