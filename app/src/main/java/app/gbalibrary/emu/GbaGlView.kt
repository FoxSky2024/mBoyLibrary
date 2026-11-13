package app.gbalibrary.emu

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Modos de filtro de imagen. */
object VideoFilter {
    const val PIXEL = 0 // GL_NEAREST (pixel perfect)
    const val SMOOTH = 1 // GL_LINEAR
    const val CRT = 2 // GL_NEAREST + scanlines
}

/** Modos de escala. */
object VideoScale {
    const val FIT = 0 // ajustar a pantalla manteniendo la proporcion
    const val ORIGINAL = 1 // multiplo entero (pixeles perfectos)
    const val STRETCH = 2 // estirar a toda la pantalla
    const val FULL = 3 // llenar la pantalla recortando
}

/**
 * Vista OpenGL ES 2.0 del emulador. La textura usa NEAREST por defecto y el filtro,
 * brillo y escala se cambian en vivo (campos @Volatile leidos por el hilo de GL).
 */
class GbaGlView(context: Context) : GLSurfaceView(context) {
    // Rectangulo (en pixeles de la vista) donde se dibuja la imagen; sirve para convertir toques en coordenadas de la DS.
    @Volatile private var destX = 0f
    @Volatile private var destY = 0f
    @Volatile private var destW = 1f
    @Volatile private var destH = 1f

    /** Pantalla tactil de la DS: el toque se manda al nucleo como puntero (0..1 sobre toda la imagen). */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!Native.isDs) return super.onTouchEvent(event)
        val idx = event.actionIndex
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                val nx = ((event.x - destX) / destW).coerceIn(0f, 1f)
                val ny = ((event.y - destY) / destH).coerceIn(0f, 1f)
                Native.setTouch(nx, ny, true)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> Native.setTouch(0f, 0f, false)
            else -> if (idx < 0) return false
        }
        return true
    }

    @Volatile var filter = VideoFilter.PIXEL
    @Volatile var scaleMode = VideoScale.FIT
    @Volatile var brightness = 1.0f

    private val renderer = GlRenderer()

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private inner class GlRenderer : GLSurfaceView.Renderer {
        // Tamano actual de la imagen: GBA 240x160, GB/GBC 160x144 (lo informa el nativo).
        private var w = 240
        private var h = 160
        private var texW = 0
        private var texH = 0
        private var uScanH = 0
        private var program = 0
        private var tex = 0
        private var aPos = 0
        private var aUv = 0
        private var uTex = 0
        private var uBright = 0
        private var uScan = 0
        private var surfaceW = 1
        private var surfaceH = 1
        private var appliedFilter = -1
        private val pixels: ByteBuffer =
            ByteBuffer.allocateDirect(1024 * 512 * 4).order(ByteOrder.nativeOrder())
        private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                // x, y, u, v  (v invertida: la fila 0 de la imagen va arriba)
                put(floatArrayOf(
                    -1f, -1f, 0f, 1f,
                    1f, -1f, 1f, 1f,
                    -1f, 1f, 0f, 0f,
                    1f, 1f, 1f, 0f,
                ))
                position(0)
            }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val vs = """
                attribute vec2 aPos;
                attribute vec2 aUv;
                varying vec2 vUv;
                void main() { gl_Position = vec4(aPos, 0.0, 1.0); vUv = aUv; }
            """.trimIndent()
            val fs = """
                precision mediump float;
                varying vec2 vUv;
                uniform sampler2D uTex;
                uniform float uBright;
                uniform float uScan;
                uniform float uLines;
                void main() {
                    vec3 c = texture2D(uTex, vUv).rgb * uBright;
                    float f = fract(vUv.y * uLines);
                    float d = abs(f - 0.5) * 2.0;
                    float line = 1.0 - uScan * 0.38 * d * d;
                    c *= line * (1.0 + uScan * 0.14);
                    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
                }
            """.trimIndent()
            program = link(compile(GLES20.GL_VERTEX_SHADER, vs), compile(GLES20.GL_FRAGMENT_SHADER, fs))
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUv = GLES20.glGetAttribLocation(program, "aUv")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            uBright = GLES20.glGetUniformLocation(program, "uBright")
            uScan = GLES20.glGetUniformLocation(program, "uScan")
            uScanH = GLES20.glGetUniformLocation(program, "uLines")

            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            tex = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            texW = 0 // se (re)crea la textura al llegar el primer frame
            texH = 0
            appliedFilter = -1 // fuerza aplicar el filtro (NEAREST/LINEAR) en el primer dibujo
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceW = max(1, width)
            surfaceH = max(1, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glViewport(0, 0, surfaceW, surfaceH)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)

            // Filtro en vivo: PIXEL y CRT usan NEAREST, SMOOTH usa LINEAR.
            val f = filter
            if (f != appliedFilter) {
                val mode = if (f == VideoFilter.SMOOTH) GLES20.GL_LINEAR else GLES20.GL_NEAREST
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, mode)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, mode)
                appliedFilter = f
            }

            pixels.position(0)
            val dims = Native.copyFrame(pixels)
            if (dims != 0) {
                val nw = dims ushr 16
                val nh = dims and 0xFFFF
                pixels.position(0)
                if (nw != texW || nh != texH) {
                    // Cambio de tamano (otro nucleo): se redimensiona la textura.
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, nw, nh, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels,
                    )
                    texW = nw; texH = nh; w = nw; h = nh
                } else {
                    GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D, 0, 0, 0, nw, nh,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels,
                    )
                }
            }
            if (texW == 0) return // aun no hay imagen

            // Rectangulo de destino segun el modo de escala.
            val sw = surfaceW.toFloat()
            val sh = surfaceH.toFloat()
            var dw: Float
            var dh: Float
            when (scaleMode) {
                VideoScale.STRETCH -> { dw = sw; dh = sh }
                VideoScale.ORIGINAL -> {
                    val s = max(1f, floor(min(sw / w, sh / h)))
                    dw = w * s; dh = h * s
                }
                VideoScale.FULL -> {
                    val s = max(sw / w, sh / h)
                    dw = w * s; dh = h * s
                }
                else -> {
                    val s = min(sw / w, sh / h)
                    dw = w * s; dh = h * s
                }
            }
            val x = ((sw - dw) / 2f).toInt()
            val y = ((sh - dh) / 2f).toInt()
            GLES20.glViewport(x, y, dw.toInt(), dh.toInt())
            destX = x.toFloat(); destY = y.toFloat(); destW = dw; destH = dh

            GLES20.glUseProgram(program)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glUniform1f(uBright, brightness)
            GLES20.glUniform1f(uScanH, h.toFloat())
            GLES20.glUniform1f(uScan, if (f == VideoFilter.CRT) 1f else 0f)

            quad.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aPos)
            quad.position(2)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aUv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUv)
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }

        private fun link(v: Int, f: Int): Int {
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v)
            GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            return p
        }
    }
}
