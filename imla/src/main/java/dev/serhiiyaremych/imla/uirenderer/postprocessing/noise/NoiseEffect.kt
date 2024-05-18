/*
 * Copyright 2024, Serhii Yaremych
 * SPDX-License-Identifier: MIT
 */

package dev.serhiiyaremych.imla.uirenderer.postprocessing.noise

import android.content.res.AssetManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.trace
import dev.romainguy.kotlin.math.Float3
import dev.serhiiyaremych.imla.renderer.Framebuffer
import dev.serhiiyaremych.imla.renderer.FramebufferAttachmentSpecification
import dev.serhiiyaremych.imla.renderer.FramebufferSpecification
import dev.serhiiyaremych.imla.renderer.SubTexture2D
import dev.serhiiyaremych.imla.renderer.Texture
import dev.serhiiyaremych.imla.renderer.Texture2D
import dev.serhiiyaremych.imla.uirenderer.RenderableScope
import dev.serhiiyaremych.imla.uirenderer.postprocessing.PostProcessingEffect
import kotlin.properties.Delegates

internal class NoiseEffect(assetManager: AssetManager) : PostProcessingEffect {

    private val shader = NoiseShaderProgram(assetManager)

    private var noiseTextureFrameBuffer: Framebuffer by Delegates.notNull()
    private var outputFrameBuffer: Framebuffer by Delegates.notNull()
    private var isNoiseTextureInitialized: Boolean = false
    private var isNoiseTextureDrawn: Boolean = false

    override fun setup(size: IntSize) {
        if (shouldResize(size)) {
            init(size)
        }
    }

    private fun init(size: IntSize) {
        if (isNoiseTextureInitialized) {
            noiseTextureFrameBuffer.destroy()
            outputFrameBuffer.destroy()
        }
        val spec = FramebufferSpecification(
            size = size,
            attachmentsSpec = FramebufferAttachmentSpecification()
        )
        noiseTextureFrameBuffer = Framebuffer.create(spec)
        outputFrameBuffer = Framebuffer.create(spec)
        isNoiseTextureInitialized = true
    }

    override fun shouldResize(size: IntSize): Boolean {
        return !isNoiseTextureInitialized || noiseTextureFrameBuffer.specification.size != size
    }

    context(RenderableScope)
    private fun drawNoiseTextureOnce() {
        if (!isNoiseTextureDrawn) {
            trace("NoiseEffect#drawNoiseTextureOnce") {
                bindFrameBuffer(noiseTextureFrameBuffer) {
                    drawScene(camera = cameraController.camera, shaderProgram = shader) {
                        drawQuad(
                            position = Float3(center),
                            size = size
                        )
                    }
                }
            }
            isNoiseTextureDrawn = true
        }
    }

    context(RenderableScope)
    override fun applyEffect(texture: Texture): Texture2D {
        val effectSize = getSize(texture)
        setup(IntSize(width = size.x.toInt(), height = size.y.toInt()))
        drawNoiseTextureOnce()
        bindFrameBuffer(outputFrameBuffer) {

        }
        return noiseTextureFrameBuffer.colorAttachmentTexture
    }

    private fun getSize(texture: Texture): IntSize {
        return when (texture) {
            is Texture2D -> IntSize(width = texture.width, height = texture.height)
            is SubTexture2D -> texture.subTextureSize
            else -> error("Unsupported texture: $texture")
        }
    }

    override fun dispose() {
        if (isNoiseTextureInitialized) {
            noiseTextureFrameBuffer.destroy()
            outputFrameBuffer.destroy()
        }
        isNoiseTextureInitialized = false
    }
}