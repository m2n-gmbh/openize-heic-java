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


/**
 * <p>
 * Type of auxiliary reference layer.
 * </p>
 */
public enum AuxiliaryReferenceType
{
    /**
     * <p>
     * Transparency layer.
     * </p>
     */
    Alpha,

    /**
     * <p>
     * Depth map layer.
     * </p>
     */
    DepthMap,

    /**
     * <p>
     * High dynamic range layer.
     * </p>
     */
    Hdr,

    /**
     * <p>
     * Undefined layer.
     * </p>
     */
    Undefined,
}
