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
import openize.isobmff.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * <p>
 * Heic image frame class.
 * Contains hevc coded data or meta data.
 * </p>
 */
public class HeicImageFrame
{
    private static final List<String> gStringSwitchMap = Arrays.asList(
                    "urn:mpeg:hevc:2015:auxid:1\u0000",
                    "urn:mpeg:hevc:2015:auxid:2\u0000",
                    "urn:com:apple:photo:2020:aux:hdrgainmap\u0000"
                    );
    private final /*UInt32*/ long id;
    private final IlocItem locationBox;
    private final HeicImage parent;
    private final BitStreamWithNalSupport stream;
    private final ImageFrameType imageType;
    /**
     * <p>
     * Indicates the type of derivative content if the frame is derived.
     * </p>
     */
    private final BoxType derivativeType;
    /**
     * <p>
     * Hevc decoder configuration information from Isobmff container.
     * </p>
     */
    HEVCDecoderConfigurationRecord hvcConfig;
    /**
     * <p>
     * Raw YUV pixel data.
     * Multidimantional array: chroma or luma index, then two-dimentional array with x and y navigation.
     * </p>
     */
    /*UInt16*/ int[][][] rawPixels;
    private boolean cashed;
    private /*UInt32*/ long ispeWidth;
    private /*UInt32*/ long ispeHeight;
    private final boolean isHidden;
    private AuxiliaryReferenceType auxiliaryReferenceType;
    private  byte numberOfChannels;
    private  byte[] bitsPerChannel;
    private Long autoalphaReference = null;
    private  byte imageRotationAngle;
    private  byte imageMirrorAxis;

    /**
     * <p>
     * Create Heic image frame object.
     * </p>
     *
     * @param stream     File stream.
     * @param parent     Parent image.
     * @param id         Frame identificator.
     * @param properties Frame properties described in container.
     */
    HeicImageFrame(BitStreamWithNalSupport stream, HeicImage parent, /*UInt32*/long id, List<Box> properties)
    {
        derivativeType = (parent.getHeader().getDerivedType(id));
        ItemInfoEntry informationBox = parent.getHeader().getInfoBoxById(id);
        locationBox = parent.getHeader().getLocationBoxById(id);

        setAuxiliaryReferenceType(AuxiliaryReferenceType.Undefined);
        imageType = ImageFrameType.codeToType(informationBox.item_type & 0xFFFFFFFFL);
        isHidden = informationBox.item_hidden;

        this.id = id;
        this.stream = stream;
        this.parent = parent;
        cashed = false;

        loadProperties(stream, properties);
    }

    /**
     * <p>
     * Type of image frame content.
     * </p>
     * @return The type of image frame content.
     */
    public final ImageFrameType getImageType()
    {
        return imageType;
    }

    /**
     * <p>
     * Width of the image frame in pixels.
     * </p>
     * @return The width of the image frame in pixels.
     */
    public final /*UInt32*/long getWidth()
    {
        return (imageRotationAngle & 0xFF) % 2 == 0 ? ispeWidth : ispeHeight;
    }

    /**
     * <p>
     * Height of the image frame in pixels.
     * </p>
     * @return The height of the image frame in pixels.
     */
    public final /*UInt32*/long getHeight()
    {
        return (imageRotationAngle & 0xFF) % 2 == 0 ? ispeHeight : ispeWidth;
    }

    /**
     * <p>
     * Indicates the presence of transparency of layer.
     * </p>
     *
     * @return True if frame is linked with alpha data frame, false otherwise.
     */
    public final boolean hasAlpha()
    {
        return autoalphaReference != null;
    }

    /**
     * <p>
     * Indicates the fact that frame is marked as hidden.
     * </p>
     *
     * @return True if frame is hidden, false otherwise.
     */
    public final boolean isHidden()
    {
        return isHidden;
    }

    /**
     * <p>
     * Indicates the fact that frame contains image data.
     * </p>
     *
     * @return True if frame is image, false otherwise.
     */
    public final boolean isImage()
    {
        return getImageType() == ImageFrameType.hvc1 || isDerived();
    }

    /**
     * <p>
     * Indicates the fact that frame contains image transform data and is inherited from another frame(-s).
     * </p>
     *
     * @return True if frame is derived, false otherwise.
     */
    public final boolean isDerived()
    {
        return getImageType() == ImageFrameType.iden || getImageType() == ImageFrameType.iovl || getImageType() == ImageFrameType.grid;
    }

    /**
     * <p>
     * Returns the type of derivative content if the frame is derived.
     * </p>
     * @return The type of derivative content if the frame is derived.
     */
    public final BoxType getDerivativeType()
    {
        return derivativeType;
    }

    /**
     * <p>
     * Returns the type of auxiliary reference layer if the frame type is auxiliary.
     * </p>
     * @return The type of auxiliary reference layer if the frame type is auxiliary.
     */
    public final AuxiliaryReferenceType getAuxiliaryReferenceType()
    {
        return auxiliaryReferenceType;
    }

    /**
     * <p>
     * Indicates the type of auxiliary reference layer if the frame type is auxiliary.
     * </p>
     * @param value the type of auxiliary reference layer if the frame type is auxiliary.
     */
    private void setAuxiliaryReferenceType(AuxiliaryReferenceType value)
    {
        auxiliaryReferenceType = value;
    }

    /**
     * <p>
     * Number of channels with color data.
     * </p>
     * @return The number of channels with color data.
     */
    public final byte getNumberOfChannels()
    {
        return numberOfChannels;
    }

    /**
     * <p>
     * Bits per channel with color data.
     * </p>
     * @return The bits per channel with color data.
     */
    public final byte[] getBitsPerChannel()
    {
        return bitsPerChannel;
    }

    /**
     * <p>
     * Get pixel data in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @return Byte array, null if frame does not contain image data.
     */
    public final byte[] getByteArray(PixelFormat pixelFormat)
    {
        return getByteArray(pixelFormat, new Rectangle());
    }

    /**
     * <p>
     * Get pixel data in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @param boundsRectangle Bounds of the requested area.
     * @return Byte array, null if frame does not contain image data.
     */
    public final byte[] getByteArray(PixelFormat pixelFormat, Rectangle boundsRectangle)
    {
        if (!isImage())
        {
            return null;
        }

        boundsRectangle = validateBounds(boundsRectangle);

        
        byte[][][] threeDim = getMultidimArray(boundsRectangle);
        threeDim = applyPixelFormat(threeDim, pixelFormat);

        int bpp = pixelFormat == PixelFormat.Rgb24 ? 3 : 4;
        
        byte[] output = new byte[boundsRectangle.getHeight() * boundsRectangle.getWidth() * bpp];

        int index = 0;
        for (int row = boundsRectangle.getTop(); row < boundsRectangle.getBottom(); row++)
        {
            for (int col = boundsRectangle.getLeft(); col < boundsRectangle.getRight(); col++)
            {
                output[index++] = threeDim[col][row][0];
                output[index++] = threeDim[col][row][1];
                output[index++] = threeDim[col][row][2];
                if (bpp == 4)
                {
                    output[index++] = threeDim[col][row][3];
                }
            }
        }

        return output;
    }

    /**
     * <p>
     * Get pixel data in the format of integer array.
     * </p>
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
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
     * Get pixel data in the format of integer array.
     * </p>
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @param boundsRectangle Bounds of the requested area.
     * @return Integer array, null if frame does not contain image data.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat, Rectangle boundsRectangle)
    {
        if (!isImage())
        {
            return null;
        }

        boundsRectangle = validateBounds(boundsRectangle);

        
        byte[][][] threeDim = getMultidimArray(boundsRectangle);
        threeDim = applyPixelFormat(threeDim, pixelFormat);

        int[] output = new int[boundsRectangle.getHeight() * boundsRectangle.getWidth()];

        int index = 0;
        for (int row = boundsRectangle.getTop(); row < boundsRectangle.getBottom(); row++)
        {
            for (int col = boundsRectangle.getLeft(); col < boundsRectangle.getRight(); col++)
            {
                output[index++] =
                        (threeDim[col][row][0] & 0xFF) << 24 |
                                (threeDim[col][row][1] & 0xFF) << 16 |
                                (threeDim[col][row][2] & 0xFF) << 8 |
                                (threeDim[col][row][3] & 0xFF);
            }
        }

        return output;
    }

    /**
     * <p>
     * Get frame text data.
     * </p>
     * <p>Exists only for mime frame types.</p>
     *
     * @return The frame text data.
     */
    public final String getTextData()
    {
        if (getImageType() != ImageFrameType.mime)
        {
            return "";
        }

        IlocItem location = null;
        for (IlocItem item : parent.getHeader().getMeta().getiloc().items)
        {
            if (item.item_ID == id)
            {
                location = item;
                break;
            }
        }

        if (location == null)
        {
            return "";
        }

        stream.setBytePosition(location.base_offset + location.extents[0].offset);
        return stream.readString();
    }

    /**
     * <p>
     * Add layer reference to the frame.
     * Used to link reference layers that are reverse-linked (alpha layer, depth map, hdr).
     * </p>
     *
     * @param id   A layer identifier.
     * @param type A layer type.
     */
    final void addLayerReference(/*UInt32*/long id, AuxiliaryReferenceType type)
    {
        if (Objects.requireNonNull(type) == AuxiliaryReferenceType.Alpha)
        {
            this.autoalphaReference = id;
        }
    }

    private byte[][][] getMultidimArray(Rectangle boundsRectangle)
    {
        
        byte[][][] rgba;

        
        byte[] databox = (locationBox.construction_method & 0xFF) == 1 ?
                parent.getHeader().getItemDataBoxContent(
                        ((locationBox.base_offset & 0xFFFFFFFFL) + (locationBox.extents[0].offset & 0xFFFFFFFFL)) & 0xFFFFFFFFL,
                        locationBox.extents[0].length) :
                new byte[0];

        switch (getImageType())
        {
            case hvc1:
                if (!cashed)
                {
                    loadHvcRawPixels();
                }

                YuvConverter converter = new YuvConverter(this);
                rgba = converter.getRgbaByteArray();
                break;
            case iden:
                /*UInt32*/
                long[] derived = parent.getHeader().getDerivedList(id);
                HeicImageFrame frame = parent.getAllFrames().get(derived[0]);
                rgba = frame.getMultidimArray(new Rectangle(0, 0, (int) frame.getWidth(), (int) frame.getHeight()));
                break;
            case iovl:
                rgba = getByteArrayForOverlay(databox);
                break;
            case grid:
                rgba = getByteArrayForGrid(databox);
                break;
            default:
                throw new IllegalStateException("Unknown image type.");
        }

        if (rgba == null)
        {
            throw new IllegalStateException("Unknown image type.");
        }

        rgba = transformImage(rgba);
        rgba = addAlphaLayer(rgba, boundsRectangle);

        return rgba;
    }

    private Rectangle validateBounds(Rectangle boundsRectangle)
    {
        if (boundsRectangle.isEmpty())
        {
            return new Rectangle(0, 0, (int) getWidth(), (int) getHeight());
        }

        if (boundsRectangle.getLeft() < 0 || boundsRectangle.getTop() < 0 ||
                boundsRectangle.getRight() > (int) getWidth() || boundsRectangle.getBottom() > (int) getHeight())
        {
            throw new IndexOutOfBoundsException("The specification of selection area cannot exceed the size of an image.");
        }

        return boundsRectangle;
    }

    private byte[][][] addAlphaLayer(byte[][][] pixels, Rectangle boundsRectangle)
    {
        if (autoalphaReference == null)
        {
            return pixels;
        }
        long tmp0 = autoalphaReference;

        
        byte[][][] alpha = parent.getAllFrames().get(tmp0 & 0xFFFFFFFFL).getMultidimArray(boundsRectangle);

        for (/*UInt32*/long row = 0; (row & 0xFFFFFFFFL) < (getHeight() & 0xFFFFFFFFL); row++)
        {
            for (/*UInt32*/long col = 0; (col & 0xFFFFFFFFL) < (getWidth() & 0xFFFFFFFFL); col++)
            {
                pixels[(int) (col)][(int) (row)][3] = alpha[(int) (col)][(int) (row)][0];
            }
        }
        return pixels;
    }

    private byte[][][] transformImage(byte[][][] pixels)
    {
        if ((imageRotationAngle & 0xFF) == 0 && (imageMirrorAxis & 0xFF) == 0)
        {
            return pixels;
        }

        
        byte[][][] rotated = new byte[(int) (getWidth() & 0xFFFFFFFFL)][(int) (getHeight() & 0xFFFFFFFFL)][4];

        /*UInt32*/
        long oldCol, oldRow;

        for (/*UInt32*/long newRow = 0; (newRow & 0xFFFFFFFFL) < (getHeight() & 0xFFFFFFFFL); newRow++)
        {
            for (/*UInt32*/long newCol = 0; (newCol & 0xFFFFFFFFL) < (getWidth() & 0xFFFFFFFFL); newCol++)
            {
                switch (imageRotationAngle)
                {
                    case 3:
                        oldCol = newRow;
                        oldRow = ((((getWidth() & 0xFFFFFFFFL) - (newCol & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                        break;
                    case 2:
                        oldCol = ((((getWidth() & 0xFFFFFFFFL) - (newCol & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                        oldRow = ((((getWidth() & 0xFFFFFFFFL) - (newRow & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                        break;
                    case 1:
                        oldCol = ((((getHeight() & 0xFFFFFFFFL) - (newRow & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                        oldRow = newCol;
                        break;
                    case 0:
                    default:
                        oldCol = newCol;
                        oldRow = newRow;
                        break;
                }

                if ((imageMirrorAxis & 0xFF) == 1) // vertical
                {
                    if ((imageRotationAngle & 0xFF) == 0 || (imageRotationAngle & 0xFF) == 2)
                        oldRow = ((((getHeight() & 0xFFFFFFFFL) - (oldRow & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                    else
                        oldCol = ((((getHeight() & 0xFFFFFFFFL) - (oldCol & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                }
                else if ((imageMirrorAxis & 0xFF) == 2) // horizontal
                {
                    if ((imageRotationAngle & 0xFF) == 0 || (imageRotationAngle & 0xFF) == 2)
                        oldCol = ((((getWidth() & 0xFFFFFFFFL) - (oldCol & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                    else
                        oldRow = ((((getWidth() & 0xFFFFFFFFL) - (oldRow & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL;
                }

                System.arraycopy(pixels[(int) (oldCol)][(int) (oldRow)], 0, rotated[(int) (newCol)][(int) (newRow)], 0, 4);
            }
        }
        return rotated;
    }

    private byte[][][] applyPixelFormat(byte[][][] rgba, PixelFormat pixelFormat)
    {
        if (pixelFormat == PixelFormat.Rgba32 || pixelFormat == PixelFormat.Rgb24)
        {
            return rgba;
        }

        
        byte r, g, b, a;

        for (/*UInt32*/long row = 0; (row & 0xFFFFFFFFL) < (getHeight() & 0xFFFFFFFFL); row++)
        {
            for (/*UInt32*/long col = 0; (col & 0xFFFFFFFFL) < (getWidth() & 0xFFFFFFFFL); col++)
            {
                r = rgba[(int) (col)][(int) (row)][0];
                g = rgba[(int) (col)][(int) (row)][1];
                b = rgba[(int) (col)][(int) (row)][2];
                a = rgba[(int) (col)][(int) (row)][3];

                switch (pixelFormat)
                {
                    case Argb32:
                        rgba[(int) (col)][(int) (row)][0] = a;
                        rgba[(int) (col)][(int) (row)][1] = r;
                        rgba[(int) (col)][(int) (row)][2] = g;
                        rgba[(int) (col)][(int) (row)][3] = b;
                        break;
                    case Bgra32:
                        rgba[(int) (col)][(int) (row)][0] = b;
                        rgba[(int) (col)][(int) (row)][1] = g;
                        rgba[(int) (col)][(int) (row)][2] = r;
                        rgba[(int) (col)][(int) (row)][3] = a;
                        break;
                }
            }
        }
        return rgba;
    }

    private /*UInt16*/int[][][] loadHvcRawPixels()
    {
        stream.setCurrentImageId(id);
        stream.setBytePosition(((locationBox.base_offset & 0xFFFFFFFFL) + (locationBox.extents[0].offset & 0xFFFFFFFFL)) & 0xFFFFFFFFL);

        /*UInt32*/long end = ((((locationBox.base_offset & 0xFFFFFFFFL)
            + (locationBox.extents[0].offset & 0xFFFFFFFFL)) & 0xFFFFFFFFL)
            + (locationBox.extents[0].length & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

        while (stream.getBitPosition()/8 < (end & 0xFFFFFFFFL))
        {
            NalUnit.parseUnit(stream);
        }

        if (stream.getContext().getPictures().containsKey(id))
            rawPixels = stream.getContext().getPictures().get(id).pixels;
        else
        {
            rawPixels = null;
            throw new IllegalArgumentException(String.format("Image #%d was not loaded [NalUnit not found].", id));
        }

        cashed = true;
        stream.deleteImageContext(id);
        return rawPixels;
    }

    private byte[][][] getByteArrayForGrid(byte[] databox)
    {
        int gridFieldLength = (((databox[1] & 0xFF) & 1) + 1) * 2;

        
        byte rows = databox[2];
        
        byte columns = databox[3];

        /*UInt32*/
        long localWidth = getByteFromIdatField(databox, 4, gridFieldLength) & 0xFFFFFFFFL;
        /*UInt32*/
        long localHeight = getByteFromIdatField(databox, 4 + gridFieldLength, gridFieldLength) & 0xFFFFFFFFL;

        int index;
        /*UInt32*/
        long[] derived = parent.getHeader().getDerivedList(id);

        HeicImageFrame frame;
        
        byte[][][] framePixels;
        
        byte[][][] output = new byte[(int) (localWidth & 0xFFFFFFFFL)][(int) (localHeight & 0xFFFFFFFFL)][4];

        for (int i = 0; i <= (rows & 0xFF); i++)
        {
            for (int j = 0; j <= (columns & 0xFF); j++)
            {
                index = i * ((columns & 0xFF) + 1) + j;
                frame = parent.getAllFrames().get(derived[index]);
                framePixels = frame.getMultidimArray(new Rectangle(0, 0, (int) frame.getWidth(), (int) frame.getHeight()));

                for (int k = 0; k < (frame.getHeight() & 0xFFFFFFFFL); k++)
                {
                    for (int l = 0; l < (frame.getWidth() & 0xFFFFFFFFL); l++)
                    {
                        if (((j * (frame.getWidth() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + l < (localWidth & 0xFFFFFFFFL) && ((i * (frame.getHeight() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + k < (localHeight & 0xFFFFFFFFL))
                        {
                            output[(int) (((j * (frame.getWidth() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + l)][(int) (((i * (frame.getHeight() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + k)][0] = framePixels[l][k][0];
                            output[(int) (((j * (frame.getWidth() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + l)][(int) (((i * (frame.getHeight() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + k)][1] = framePixels[l][k][1];
                            output[(int) (((j * (frame.getWidth() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + l)][(int) (((i * (frame.getHeight() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + k)][2] = framePixels[l][k][2];
                            output[(int) (((j * (frame.getWidth() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + l)][(int) (((i * (frame.getHeight() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + k)][3] = framePixels[l][k][3];
                        }
                    }
                }
            }
        }
        return output;
    }

    private byte[][][] getByteArrayForOverlay(byte[] databox)
    {
        /*UInt32*/
        long[] derived = parent.getHeader().getDerivedList(id);

        int iovlFieldLength = (((databox[1] & 0xFF) & 1) + 1) * 2;
        int index = 2;

        /*UInt16*/
        //    int[] canvas_fill_value = new /*UInt16*/int[4];
        for (int j = 0; j < 4; j++)
        {
     //       canvas_fill_value[j] = getByteFromIdatField(databox, index, 2) & 0xFFFF;
            index += 2;
        }

        /*UInt32*/
        long outputWidth = getByteFromIdatField(databox, index, iovlFieldLength) & 0xFFFFFFFFL;
        /*UInt32*/
        long outputHeight = getByteFromIdatField(databox, index + iovlFieldLength, iovlFieldLength) & 0xFFFFFFFFL;
        index += 2 * iovlFieldLength;

        int[] horizontal_offset = new int[derived.length];
        int[] vertical_offset = new int[derived.length];

        for (int i = 0; i < derived.length; i++)
        {
            horizontal_offset[i] = getByteFromIdatField(databox, index, iovlFieldLength);
            vertical_offset[i] = getByteFromIdatField(databox, index + iovlFieldLength, iovlFieldLength);
            index += 2 * iovlFieldLength;
        }

        HeicImageFrame frame;
        
        byte[][][] framePixels;
        
        byte[][][] output = new byte[(int) (outputWidth & 0xFFFFFFFFL)][(int) (outputHeight & 0xFFFFFFFFL)][4];

        final long height = getHeight() & 0xFFFFFFFFL;
        final long width = getWidth() & 0xFFFFFFFFL;
        for (/*UInt32*/long row = 0; (row & 0xFFFFFFFFL) < height; row++)
        {
            for (/*UInt32*/long col = 0; (col & 0xFFFFFFFFL) < width; col++)
            {
                output[(int) (col)][(int) (row)][3] = (byte) 255;
            }
        }

        for (int i = 0; i < derived.length; i++)
        {
            frame = parent.getAllFrames().get(derived[i]);
            framePixels = frame.getMultidimArray(new Rectangle(0, 0, (int) frame.getWidth(), (int) frame.getHeight()));

            for (int k = 0; k < (frame.getHeight() & 0xFFFFFFFFL); k++)
            {
                for (int l = 0; l < (frame.getWidth() & 0xFFFFFFFFL); l++)
                {
                    if (horizontal_offset[i] + l < (outputWidth & 0xFFFFFFFFL) && vertical_offset[i] + k < (outputHeight & 0xFFFFFFFFL))
                    {
                        output[horizontal_offset[i] + l][vertical_offset[i] + k][0] = framePixels[l][k][0];
                        output[horizontal_offset[i] + l][vertical_offset[i] + k][1] = framePixels[l][k][1];
                        output[horizontal_offset[i] + l][vertical_offset[i] + k][2] = framePixels[l][k][2];
                        output[horizontal_offset[i] + l][vertical_offset[i] + k][3] = framePixels[l][k][3];
                    }
                }
            }
        }
        return output;
    }

    private int getByteFromIdatField(byte[] arr, int offset, int length)
    {
        int value = arr[offset] & 0xFF;

        for (int i = 1; i < length; i++)
        {
            value = (value << 8) | (arr[offset + i] & 0xFF);
        }

        return value;
    }

    private void loadProperties(BitStreamWithNalSupport stream, List<Box> properties)
    {
        for (Box item : properties)
        {
            switch (item.type)
            {
                case hvcC:
                    HEVCConfigurationBox config = (HEVCConfigurationBox) item;
                    stream.createNewImageContext(id);
                    stream.setBytePosition(config.offset);
                    hvcConfig = new HEVCDecoderConfigurationRecord(stream);
                    config.record = hvcConfig; // gui
                    break;
                case ispe:
                    ImageSpatialExtentsProperty ispe = (ImageSpatialExtentsProperty) item;
                    ispeWidth = ispe.image_width;
                    ispeHeight = ispe.image_height;
                    break;
                case pasp:
                    //PixelAspectRatioBox pasp = (PixelAspectRatioBox) item;
                    break;
                case colr:
                    //ColourInformationBox colr = (ColourInformationBox) item;
                    break;
                case pixi:
                    PixelInformationProperty pixi = (PixelInformationProperty) item;
                    this.numberOfChannels = pixi.num_channels;
                    this.bitsPerChannel = pixi.bits_per_channel;
                    break;
                case rloc:
                    //RelativeLocationProperty rloc = (RelativeLocationProperty) item;
                    break;
                case auxC:
                    AuxiliaryTypeProperty auxC = (AuxiliaryTypeProperty) item;

                    setAuxiliaryReferenceType(AuxiliaryReferenceType.Undefined);

                    switch (gStringSwitchMap.indexOf(auxC.aux_type))
                    {
                        case /*"urn:mpeg:hevc:2015:auxid:1\u0000"*/0:
                            setAuxiliaryReferenceType(AuxiliaryReferenceType.Alpha);
                            break;
                        case /*"urn:mpeg:hevc:2015:auxid:2\u0000"*/1:
                            setAuxiliaryReferenceType(AuxiliaryReferenceType.DepthMap);
                            break;
                        case /*"urn:com:apple:photo:2020:aux:hdrgainmap\u0000"*/2:
                            setAuxiliaryReferenceType(AuxiliaryReferenceType.Hdr);
                            break;
                    }

                    /*UInt32*/
                    long[] derived = parent.getHeader().getDerivedList(id);
                    for (/*UInt32*/long derivedId : derived)
                    {
                        parent.getAllFrames().get(derivedId).addLayerReference(id, getAuxiliaryReferenceType());
                    }
                    break;
                case clap:
                    CleanApertureBox clap = (CleanApertureBox) item;
                    long left = clap.getLeftRounded(ispeWidth);
                    long top = clap.getTopRounded(ispeHeight);
                    // TODO: apply left/top offset (although, it's often zero)
                    ispeWidth = clap.getWidthRounded();
                    ispeHeight = clap.getHeightRounded();
                    break;
                case irot:
                    ImageRotation irot = (ImageRotation) item;
                    imageRotationAngle = irot.angle;
                    break;
                case lsel:
                    //LayerSelectorProperty lsel = (LayerSelectorProperty) item;
                    break;
                case imir:
                    ImageMirror imir = (ImageMirror) item;
                    imageMirrorAxis = ((byte) ((imir.axis & 0xFF) + 1));
                    break;
                default:
                    break;
            }
        }
    }
}

