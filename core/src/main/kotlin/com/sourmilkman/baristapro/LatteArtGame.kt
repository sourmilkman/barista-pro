package com.sourmilkman.baristapro

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.sqrt

class LatteArtGame : ApplicationAdapter() {
    private val worldWidth = 480f
    private val worldHeight = 900f
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: FitViewport
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private val drops = mutableListOf<MilkDrop>()
    private val pointer = Vector2(worldWidth / 2, 390f)
    private val cup = Vector2(worldWidth / 2, 440f)
    private var height = 0.35f
    private var pouring = false
    private var accumulator = 0f

    override fun create() {
        camera = OrthographicCamera()
        viewport = FitViewport(worldWidth, worldHeight, camera)
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont().apply { data.setScale(1.15f) }
    }

    override fun resize(width: Int, heightPx: Int) = viewport.update(width, heightPx, true)

    override fun render() {
        val dt = Gdx.graphics.deltaTime.coerceAtMost(1f / 30f)
        handleInput(dt)
        update(dt)
        Gdx.gl.glClearColor(0.035f, 0.035f, 0.045f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined
        drawScene()
        drawText()
    }

    private fun handleInput(dt: Float) {
        pouring = false
        for (index in 0 until 5) {
            if (!Gdx.input.isTouched(index)) continue
            val p = viewport.unproject(Vector2(Gdx.input.getX(index).toFloat(), Gdx.input.getY(index).toFloat()))
            when {
                p.y < 115f && p.x > 250f -> pouring = true
                p.y in 140f..190f -> height = ((p.x - 55f) / 370f).coerceIn(0f, 1f)
                p.y > 205f -> pointer.set(p)
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) pouring = true
        if (pouring) accumulator += dt else accumulator = 0f
        while (accumulator >= 0.028f) {
            accumulator -= 0.028f
            emitDrop()
        }
    }

    private fun emitDrop() {
        val dx = pointer.x - cup.x
        val dy = pointer.y - cup.y
        if (dx * dx + dy * dy > 171f * 171f) return
        val visibleFoam = 1f - height
        drops += MilkDrop(pointer.x, pointer.y, 4f + visibleFoam * 4f, visibleFoam, MathUtils.random(-8f, 8f), MathUtils.random(-8f, 8f))
        if (drops.size > 850) drops.removeAt(0)
    }

    private fun update(dt: Float) {
        drops.forEach { drop ->
            val dx = drop.x - cup.x
            val dy = drop.y - cup.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val edge = (distance / 171f).coerceIn(0f, 1f)
            drop.vx += (-dy / distance) * 3f * dt
            drop.vy += (dx / distance) * 3f * dt
            drop.vx *= 0.965f
            drop.vy *= 0.965f
            drop.x += drop.vx * dt
            drop.y += drop.vy * dt
            drop.radius = (drop.radius + dt * (3.3f + height * 5f)).coerceAtMost(18f)
            if (edge > .95f) { drop.vx -= dx * dt * .8f; drop.vy -= dy * dt * .8f }
        }
    }

    private fun drawScene() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0.10f, 0.10f, 0.12f, 1f)
        shapes.circle(cup.x, cup.y, 198f, 96)
        shapes.color = Color(0.88f, 0.86f, 0.82f, 1f)
        shapes.circle(cup.x, cup.y, 187f, 96)
        shapes.color = Color(0.20f, 0.075f, 0.035f, 1f)
        shapes.circle(cup.x, cup.y, 171f, 96)
        drops.forEach { d ->
            shapes.color = Color(0.93f, 0.86f, 0.72f, 0.18f + d.foam * 0.74f)
            shapes.circle(d.x, d.y, d.radius, 20)
        }
        shapes.color = Color(0.95f, 0.91f, 0.83f, 1f)
        shapes.circle(pointer.x, pointer.y, 7f, 24)
        shapes.color = Color(0.50f, 0.50f, 0.54f, 1f)
        shapes.rect(55f, 157f, 370f, 7f)
        shapes.color = Color(0.93f, 0.84f, 0.71f, 1f)
        shapes.circle(55f + height * 370f, 160.5f, 15f, 24)
        shapes.color = if (pouring) Color(0.93f, 0.84f, 0.71f, 1f) else Color(0.16f, 0.16f, 0.19f, 1f)
        shapes.rect(255f, 45f, 170f, 65f)
        shapes.color = Color(0.16f, 0.16f, 0.19f, 1f)
        shapes.rect(55f, 45f, 170f, 65f)
        shapes.end()
    }

    private fun drawText() {
        batch.begin()
        font.color = Color.WHITE
        font.data.setScale(1.55f)
        font.draw(batch, "POUR LAB", 32f, 850f)
        font.data.setScale(.82f)
        font.color = Color(0.64f, 0.64f, 0.69f, 1f)
        font.draw(batch, "Drag above the cup to position the pitcher", 32f, 817f)
        font.draw(batch, "PITCHER HEIGHT  ${if (height < .45f) "LOW / FOAM" else "HIGH / BLEND"}", 55f, 205f)
        font.color = Color.WHITE
        font.data.setScale(1.05f)
        font.draw(batch, "RESET", 108f, 86f)
        font.color = if (pouring) Color(0.10f, 0.08f, 0.06f, 1f) else Color.WHITE
        font.draw(batch, "HOLD TO POUR", 275f, 86f)
        font.color = Color(0.44f, 0.44f, 0.49f, 1f)
        font.data.setScale(.65f)
        font.draw(batch, "BUILD 0.1.0 • GPT-5 CODEX", 32f, 24f)
        batch.end()

        if (Gdx.input.justTouched()) {
            val p = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
            if (p.y < 115f && p.x < 240f) drops.clear()
        }
    }

    override fun dispose() { shapes.dispose(); batch.dispose(); font.dispose() }

    private data class MilkDrop(var x: Float, var y: Float, var radius: Float, val foam: Float, var vx: Float, var vy: Float)
}
