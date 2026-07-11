package com.sourmilkman.baristapro

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class LatteArtGame : ApplicationAdapter() {
    private val worldWidth = 480f
    private val worldHeight = 900f
    private val cup = Vector2(240f, 460f)
    private val cupRadius = 166f
    private val ringRadius = 202f
    private val gridSize = 64
    private val cellSize = cupRadius * 2f / gridSize
    private val cellArea = gridSize * gridSize

    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: FitViewport
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont

    private var foam = FloatArray(cellArea)
    private var nextFoam = FloatArray(cellArea)
    private var velocityX = FloatArray(cellArea)
    private var velocityY = FloatArray(cellArea)
    private var nextVelocityX = FloatArray(cellArea)
    private var nextVelocityY = FloatArray(cellArea)
    private val pointer = Vector2(cup.x, cup.y)
    private val lastPointer = Vector2(pointer)
    private var flowAngle = MathUtils.PI / 2f
    private var touchMode = TouchMode.NONE
    private var pourDuration = 0f
    private var totalMilk = 0f
    private var gesture = "READY"
    private var cutThrough = false

    override fun create() {
        camera = OrthographicCamera()
        viewport = FitViewport(worldWidth, worldHeight, camera)
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
    }

    override fun resize(width: Int, height: Int) = viewport.update(width, height, true)

    override fun render() {
        val dt = Gdx.graphics.deltaTime.coerceIn(0f, 1f / 30f)
        handleInput(dt)
        repeat(2) { simulate(dt * .5f) }
        Gdx.gl.glClearColor(.025f, .025f, .032f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined
        drawScene()
        drawText()
    }

    private fun handleInput(dt: Float) {
        if (!Gdx.input.isTouched) {
            if (touchMode == TouchMode.POUR) gesture = "READY"
            touchMode = TouchMode.NONE
            pourDuration = 0f
            cutThrough = false
            return
        }

        val point = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
        val dx = point.x - cup.x
        val dy = point.y - cup.y
        val distance = sqrt(dx * dx + dy * dy)

        if (touchMode == TouchMode.NONE) {
            touchMode = when {
                point.y < 92f -> {
                    clearSurface()
                    TouchMode.BUTTON
                }
                distance in (cupRadius + 13f)..(ringRadius + 28f) -> TouchMode.RING
                distance <= cupRadius -> {
                    pointer.set(point)
                    lastPointer.set(point)
                    TouchMode.POUR
                }
                else -> TouchMode.BUTTON
            }
        }

        when (touchMode) {
            TouchMode.RING -> {
                flowAngle = atan2(dy, dx)
                gesture = "SET FLOW DIRECTION"
            }
            TouchMode.POUR -> {
                if (distance <= cupRadius) pointer.set(point)
                val moveX = (pointer.x - lastPointer.x) / dt.coerceAtLeast(.001f)
                val moveY = (pointer.y - lastPointer.y) / dt.coerceAtLeast(.001f)
                val directionX = cos(flowAngle)
                val directionY = sin(flowAngle)
                val forwardSpeed = moveX * directionX + moveY * directionY
                val sidewaysSpeed = -moveX * directionY + moveY * directionX
                val speed = sqrt(moveX * moveX + moveY * moveY)
                cutThrough = speed > 245f && forwardSpeed > 190f
                gesture = when {
                    cutThrough -> "CUT-THROUGH"
                    forwardSpeed < -38f && abs(sidewaysSpeed) > 22f -> "PULL-BACK JIGGLE"
                    forwardSpeed < -38f -> "PULL BACK"
                    forwardSpeed > 38f -> "PUSH FORWARD"
                    abs(sidewaysSpeed) > 24f -> "SIDE-TO-SIDE JIGGLE"
                    else -> "STEADY POUR"
                }
                pourDuration += dt
                injectFoam(dt, moveX, moveY)
                lastPointer.set(pointer)
            }
            else -> Unit
        }
    }

    private fun injectFoam(dt: Float, movementX: Float, movementY: Float) {
        val fill = (totalMilk / 5.5f).coerceIn(0f, 1f)
        val flowRate = if (cutThrough) .27f else (.58f + pourDuration * .24f).coerceAtMost(1.18f)
        val radius = if (cutThrough) 1.25f else 2.3f + flowRate * 1.35f
        val directionX = cos(flowAngle)
        val directionY = sin(flowAngle)
        val momentum = if (cutThrough) 128f else 70f * (1f - fill * .52f)
        val gx = worldToGridX(pointer.x)
        val gy = worldToGridY(pointer.y)
        val reach = (radius * 2f).toInt() + 1

        for (y in (gy.toInt() - reach)..(gy.toInt() + reach)) {
            for (x in (gx.toInt() - reach)..(gx.toInt() + reach)) {
                if (!insideGrid(x, y)) continue
                val ox = x + .5f - gx
                val oy = y + .5f - gy
                val distanceSquared = ox * ox + oy * oy
                val weight = exp(-distanceSquared / (radius * radius))
                if (weight < .02f) continue
                val index = index(x, y)
                foam[index] = (foam[index] + weight * flowRate * dt * 2.15f).coerceAtMost(1f)
                val radialLength = sqrt(distanceSquared).coerceAtLeast(.25f)
                val radialPressure = if (cutThrough) 5f else 25f * flowRate
                velocityX[index] += (directionX * momentum + ox / radialLength * radialPressure + movementX * .08f) * weight * dt
                velocityY[index] += (directionY * momentum + oy / radialLength * radialPressure + movementY * .08f) * weight * dt
            }
        }
        totalMilk = (totalMilk + flowRate * dt).coerceAtMost(6f)
    }

    private fun simulate(dt: Float) {
        val fill = (totalMilk / 5.5f).coerceIn(0f, 1f)
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val i = index(x, y)
                if (!insideCupCell(x, y)) {
                    nextFoam[i] = 0f
                    nextVelocityX[i] = 0f
                    nextVelocityY[i] = 0f
                    continue
                }

                val nx = (x + .5f - gridSize / 2f) / (gridSize / 2f)
                val ny = (y + .5f - gridSize / 2f) / (gridSize / 2f)
                val radial = sqrt(nx * nx + ny * ny)
                val rimT = ((radial - .62f) / .38f).coerceIn(0f, 1f)
                val rimResistance = rimT * rimT * (3f - 2f * rimT)
                val damping = (1f - dt * (1.8f + fill * 3.9f + rimResistance * 10.5f)).coerceIn(0f, 1f)

                val left = foam[index((x - 1).coerceAtLeast(0), y)]
                val right = foam[index((x + 1).coerceAtMost(gridSize - 1), y)]
                val down = foam[index(x, (y - 1).coerceAtLeast(0))]
                val up = foam[index(x, (y + 1).coerceAtMost(gridSize - 1))]
                val pressure = 32f * (1f - fill * .42f)
                var vx = (velocityX[i] - (right - left) * pressure * dt) * damping
                var vy = (velocityY[i] - (up - down) * pressure * dt) * damping

                if (radial > .91f) {
                    val outward = vx * nx + vy * ny
                    if (outward > 0f) {
                        vx -= nx * outward * .92f
                        vy -= ny * outward * .92f
                    }
                }

                val sourceX = x - vx * dt / cellSize
                val sourceY = y - vy * dt / cellSize
                val advected = sample(foam, sourceX, sourceY)
                val smoothed = (left + right + down + up) * .25f
                val diffusion = if (cutThrough) .015f else .045f
                nextFoam[i] = MathUtils.lerp(advected, smoothed, diffusion * dt * 30f).coerceIn(0f, 1f)
                nextVelocityX[i] = vx
                nextVelocityY[i] = vy
            }
        }
        foam = nextFoam.also { nextFoam = foam }
        velocityX = nextVelocityX.also { nextVelocityX = velocityX }
        velocityY = nextVelocityY.also { nextVelocityY = velocityY }
    }

    private fun sample(field: FloatArray, x: Float, y: Float): Float {
        val x0 = x.toInt().coerceIn(0, gridSize - 1)
        val y0 = y.toInt().coerceIn(0, gridSize - 1)
        val x1 = (x0 + 1).coerceAtMost(gridSize - 1)
        val y1 = (y0 + 1).coerceAtMost(gridSize - 1)
        val tx = (x - x0).coerceIn(0f, 1f)
        val ty = (y - y0).coerceIn(0f, 1f)
        return MathUtils.lerp(
            MathUtils.lerp(field[index(x0, y0)], field[index(x1, y0)], tx),
            MathUtils.lerp(field[index(x0, y1)], field[index(x1, y1)], tx),
            ty
        )
    }

    private fun drawScene() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(.12f, .12f, .14f, 1f)
        shapes.circle(cup.x, cup.y, ringRadius + 8f, 120)
        shapes.color = Color(.035f, .035f, .043f, 1f)
        shapes.circle(cup.x, cup.y, ringRadius - 7f, 120)
        shapes.color = Color(.86f, .84f, .80f, 1f)
        shapes.circle(cup.x, cup.y, cupRadius + 12f, 120)
        shapes.color = Color(.20f, .068f, .028f, 1f)
        shapes.circle(cup.x, cup.y, cupRadius, 120)

        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                if (!insideCupCell(x, y)) continue
                val amount = foam[index(x, y)]
                if (amount < .008f) continue
                val px = cup.x - cupRadius + (x + .5f) * cellSize
                val py = cup.y - cupRadius + (y + .5f) * cellSize
                val cream = .58f + amount * .38f
                shapes.color = Color(cream, .46f + amount * .46f, .30f + amount * .55f, (.16f + amount * .84f).coerceAtMost(1f))
                shapes.circle(px, py, cellSize * .72f, 10)
            }
        }

        val directionX = cos(flowAngle)
        val directionY = sin(flowAngle)
        val arrowStartX = cup.x - directionX * (ringRadius - 8f)
        val arrowStartY = cup.y - directionY * (ringRadius - 8f)
        val arrowEndX = cup.x - directionX * (cupRadius + 18f)
        val arrowEndY = cup.y - directionY * (cupRadius + 18f)
        shapes.color = Color(.94f, .79f, .58f, 1f)
        shapes.rectLine(arrowStartX, arrowStartY, arrowEndX, arrowEndY, 7f)
        shapes.circle(arrowStartX, arrowStartY, 8f, 20)

        if (touchMode == TouchMode.POUR) {
            shapes.color = if (cutThrough) Color(.95f, .42f, .28f, 1f) else Color.WHITE
            shapes.circle(pointer.x, pointer.y, if (cutThrough) 5f else 8f, 24)
        }

        shapes.color = Color(.15f, .15f, .18f, 1f)
        shapes.rect(32f, 32f, 416f, 60f)
        shapes.end()
    }

    private fun drawText() {
        batch.begin()
        font.data.setScale(1.55f)
        font.color = Color.WHITE
        font.draw(batch, "POUR LAB", 28f, 858f)
        font.data.setScale(.78f)
        font.color = Color(.62f, .62f, .67f, 1f)
        font.draw(batch, "Rotate the ring, then touch and move inside the cup", 28f, 824f)
        font.color = if (cutThrough) Color(.98f, .48f, .32f, 1f) else Color(.94f, .79f, .58f, 1f)
        font.data.setScale(.92f)
        font.draw(batch, gesture, 28f, 785f)
        font.color = Color(.66f, .66f, .70f, 1f)
        font.data.setScale(.72f)
        font.draw(batch, "FLOW", cup.x - 21f, cup.y + ringRadius + 29f)
        font.color = Color.WHITE
        font.data.setScale(1.05f)
        font.draw(batch, "RESET CUP", 177f, 70f)
        font.color = Color(.43f, .43f, .48f, 1f)
        font.data.setScale(.64f)
        font.draw(batch, "BUILD 0.2.0 • GPT-5 CODEX", 28f, 18f)
        batch.end()
    }

    private fun clearSurface() {
        foam.fill(0f)
        nextFoam.fill(0f)
        velocityX.fill(0f)
        velocityY.fill(0f)
        nextVelocityX.fill(0f)
        nextVelocityY.fill(0f)
        totalMilk = 0f
        gesture = "READY"
    }

    private fun worldToGridX(x: Float) = (x - (cup.x - cupRadius)) / cellSize
    private fun worldToGridY(y: Float) = (y - (cup.y - cupRadius)) / cellSize
    private fun index(x: Int, y: Int) = y * gridSize + x
    private fun insideGrid(x: Int, y: Int) = x in 0 until gridSize && y in 0 until gridSize && insideCupCell(x, y)
    private fun insideCupCell(x: Int, y: Int): Boolean {
        val dx = x + .5f - gridSize / 2f
        val dy = y + .5f - gridSize / 2f
        return dx * dx + dy * dy < (gridSize / 2f - .7f) * (gridSize / 2f - .7f)
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }

    private enum class TouchMode { NONE, RING, POUR, BUTTON }
}
