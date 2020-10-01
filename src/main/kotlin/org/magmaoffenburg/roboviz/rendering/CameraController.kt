package org.magmaoffenburg.roboviz.rendering

import com.jogamp.opengl.GLAutoDrawable
import jsgl.jogl.view.FPCamera
import jsgl.math.vector.Vec2f
import jsgl.math.vector.Vec3f
import rv.ui.SceneObjectPicker
import rv.ui.view.ICameraController
import rv.ui.view.SimsparkController
import rv.ui.view.TargetTrackerCamera

class CameraController(private val drawable: GLAutoDrawable) {

    companion object {
        lateinit var camera: FPCamera
        lateinit var cameraController: ICameraController
        lateinit var trackerCamera: TargetTrackerCamera

        lateinit var objectPicker: SceneObjectPicker
    }

    init {
        initCamera()
        initCameraController()
        initTargetTrackerCamera()
        initPicker()
    }

    private fun initCamera() {
        val pos = Vec3f(0f, 7f, -10f) // 3D camera position
        val rot = Vec2f(-40f, 180f) // camera x,y rotations
        val fov = 45f // field of view (degrees)
        val near = 0.1f // near clip plane distance
        val far = 200f // far clip plane distance

        camera = FPCamera(pos, rot, fov, near, far)

        if (drawable.chosenGLCapabilities.stereo) {
            camera.focalLength = 8f
            camera.eyeSeparation = 3 / 20.0f
        }
    }

    private fun initCameraController() {
        val controller = SimsparkController()
        cameraController = controller
        Renderer.world.gameState.addListener(controller)
    }

    private fun initTargetTrackerCamera() {
        trackerCamera = TargetTrackerCamera(camera, Renderer.world.gameState)
    }

    private fun initPicker() {
        objectPicker = SceneObjectPicker(Renderer.world, camera)
    }

    fun update(elapsedMS: Double) {
        camera.update(elapsedMS)
        cameraController.update(elapsedMS)
        trackerCamera.update(Renderer.instance.screen)
    }

}