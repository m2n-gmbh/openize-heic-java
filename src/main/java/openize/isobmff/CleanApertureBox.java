/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2025 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package openize.isobmff;

import openize.isobmff.io.BitStreamReader;


/**
 * <p>
 * The clean aperture transformative item property defines a cropping transformation of the input image.
 * </p>
 */
public class CleanApertureBox extends Box
{
    /**
     * <p>
     * A numerator of the fractional number which defines the exact clean aperture width, in counted pixels, of the image.
     * </p>
     */
    public final /*UInt32*/ long cleanApertureWidthN;

    /**
     * <p>
     * A denominator of the fractional number which defines the exact clean aperture width, in counted pixels, of the image.
     * </p>
     */
    public final /*UInt32*/ long cleanApertureWidthD;

    /**
     * <p>
     * A numerator of the fractional number which defines the exact clean aperture height, in counted pixels, of the image.
     * </p>
     */
    public final /*UInt32*/ long cleanApertureHeightN;

    /**
     * <p>
     * A denominator of the fractional number which defines the exact clean aperture height, in counted pixels, of the image.
     * </p>
     */
    public final /*UInt32*/ long cleanApertureHeightD;

    /**
     * <p>
     * A numerator of the fractional number which defines the horizontal offset of clean aperture centre minus(width‐1)/2. Typically 0.
     * </p>
     */
    public final int horizOffN;

    /**
     * <p>
     * A denominator of the fractional number which defines the horizontal offset of clean aperture centre minus(width‐1)/2. Typically 0.
     * </p>
     */
    public final /*UInt32*/ long horizOffD;

    /**
     * <p>
     * A numerator of the fractional number which defines the vertical offset of clean aperture centre minus(height‐1)/2. Typically 0.
     * </p>
     */
    public final int vertOffN;

    /**
     * <p>
     * A denominator of the fractional number which defines the vertical offset of clean aperture centre minus(height‐1)/2. Typically 0.
     * </p>
     */
    public final /*UInt32*/ long vertOffD;

    /**
     * <p>
     * Create the box object from the bitstream and box size.
     * </p>
     *
     * @param stream File stream.
     * @param size   Box size in bytes.
     */
    public CleanApertureBox(BitStreamReader stream, /*UInt64*/long size)
    {
        super(BoxType.clap, size);

        cleanApertureWidthN = stream.read(32) & 0xFFFFFFFFL;
        cleanApertureWidthD = stream.read(32) & 0xFFFFFFFFL;
        cleanApertureHeightN = stream.read(32) & 0xFFFFFFFFL;
        cleanApertureHeightD = stream.read(32) & 0xFFFFFFFFL;
        horizOffN = stream.read(32);
        horizOffD = stream.read(32) & 0xFFFFFFFFL;
        vertOffN = stream.read(32);
        vertOffD = stream.read(32) & 0xFFFFFFFFL;
    }

    public long getWidthRounded()
    {
        return round(cleanApertureWidthN, cleanApertureWidthD);
    }

    public long getHeightRounded()
    {
        return round(cleanApertureHeightN, cleanApertureHeightD);
    }

    public long getLeftRounded(long imageWith)
    {
        double offset = (double)horizOffN / (double)horizOffD;
        double imageCenter = (double)(imageWith-1) / (double)2;
        double apertureCenter = ((double)(cleanApertureWidthN-1) / (double)cleanApertureWidthD) / 2;
        return (long)Math.floor(offset + imageCenter - apertureCenter);
    }

    public long getTopRounded(long imageHeight)
    {
        double offset = (double)vertOffN / (double)vertOffD;
        double imageCenter = (double)(imageHeight-1) / (double)2;
        double apertureCenter = ((double)(cleanApertureHeightN-1) / (double)cleanApertureHeightD) / 2;
        return Math.round(offset + imageCenter - apertureCenter);
    }

    private static long round(long numerator, long denominator)
    {
        return (numerator + denominator / 2) / denominator;
    }
    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public String toString()
    {
        return String.format("%s size: %d", this.type, size);
    }
}

