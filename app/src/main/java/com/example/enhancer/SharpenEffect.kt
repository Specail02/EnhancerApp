@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.enhancer

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** تأثير حدة (Unsharp Mask) يعمل على الـ GPU لكل إطار فيديو. */
class SharpenEffect(private val amount: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SharpenShaderProgram(useHdr, amount)
}

private class SharpenShaderProgram(
    useHdr: Boolean,
    private val amount: Float
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram = GlProgram(VERTEX, FRAGMENT)

    init {
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        glProgram.setFloatsUniform("uTexel", floatArrayOf(1f / inputWidth, 1f / inputHeight))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        glProgram.use()
        glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
        glProgram.setFloatUniform("uAmount", amount)
        glProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        glProgram.delete()
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vTex;
            void main() {
              gl_Position = aFramePosition;
              vTex = aFramePosition.xy * 0.5 + 0.5;
            }
        """

        const val FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexel;
            uniform float uAmount;
            varying vec2 vTex;
            void main() {
              vec4 c = texture2D(uTexSampler, vTex);
              vec3 blur = (
                texture2D(uTexSampler, vTex + vec2(uTexel.x, 0.0)).rgb +
                texture2D(uTexSampler, vTex - vec2(uTexel.x, 0.0)).rgb +
                texture2D(uTexSampler, vTex + vec2(0.0, uTexel.y)).rgb +
                texture2D(uTexSampler, vTex - vec2(0.0, uTexel.y)).rgb) * 0.25;
              vec3 outc = c.rgb + (c.rgb - blur) * uAmount;
              gl_FragColor = vec4(clamp(outc, 0.0, 1.0), c.a);
            }
        """
    }
}
