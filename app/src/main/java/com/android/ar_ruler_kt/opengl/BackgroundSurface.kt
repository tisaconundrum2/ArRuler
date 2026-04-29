package com.android.ar_ruler_kt.opengl

import android.content.Context
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import com.android.ar_ruler_kt.IViewInterface
import com.android.ar_ruler_kt.helper.DisplayRotationHelper
import com.google.ar.core.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * @author：TianLong
 * @date：2022/6/27 23:32
 * @detail：Background rendering class
 */
class BackgroundSurface: GLSurface ,SessionImpl {
    lateinit var backgroundRenderer:BackgroundRenderer
    lateinit var bitmapRenderer: BitmapRenderer
    lateinit var pointRenderer: PointRenderer
    lateinit var lineRenderer: LineRenderer
    lateinit var pictureRenderer: PictureRenderer
    val motionEvent:MotionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0) // No specific meaning
    var viewMatrix = FloatArray(16)
    var projectMatrix = FloatArray(16)
    var iViewInterface:IViewInterface? = null
    private val anchorQueue:ConcurrentLinkedQueue<MotionEvent> by lazy {ConcurrentLinkedQueue<MotionEvent>() }
    private val limitsSize = 10 // Maximum number of points
    private val anchorList = ArrayList<Anchor>(limitsSize)
    private val displayRotationHelper by lazy { DisplayRotationHelper(context) }
    override var session : Session? = null
    var detectPointOrPlane = false

    constructor(context: Context) : super(context,null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs){
        initialize(context)
    }

    private fun initialize(context: Context){
        backgroundRenderer = BackgroundRenderer(context)
        bitmapRenderer= BitmapRenderer(context)
        pointRenderer = PointRenderer(context)
        lineRenderer = LineRenderer(context)
        pictureRenderer = PictureRenderer(context)
        // Set to identity matrix
        Matrix.setIdentityM(viewMatrix,0)
        Matrix.setIdentityM(projectMatrix,0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        super.onSurfaceCreated(gl, config)
        backgroundRenderer.onSurfaceCreated()
        bitmapRenderer.onSurfaceCreated()
        pointRenderer.onSurfaceCreated()
        lineRenderer.onSurfaceCreated()
        pictureRenderer.onSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        super.onSurfaceChanged(gl,width,height)
        displayRotationHelper.onSurfaceChanged(width, height)
        backgroundRenderer.onSurfaceChanged(width,height)
        bitmapRenderer.onSurfaceChanged(width,height)
        pointRenderer.onSurfaceChanged(width,height)
        pictureRenderer.onSurfaceChanged(width,height)
    }

    override fun onDrawFrame(gl: GL10?) {
        super.onDrawFrame(gl)
        session?.run {
            displayRotationHelper.updateSessionIfNeeded(session)
            this.setCameraTextureName(backgroundRenderer.textureIds[0])
            // Update ARCore frame data
            val frame = this.update()
            // Get vertex and texture data from ARCore
            if (frame.hasDisplayGeometryChanged()) {
                frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    backgroundRenderer.vertexBuffer,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    backgroundRenderer.textureBuffer)
            }

            if (frame.timestamp == 0L) {
                detectFailed("Time is 0")
                return
            }
            backgroundRenderer.onDrawFrame()

            val tt = System.currentTimeMillis()
            val camera = frame.camera

            if (camera.trackingState!=TrackingState.TRACKING){
               detectFailed(camera.trackingState.name)
                Log.e(TAG,"It's error  because camera trackingState is ${camera.trackingState.name}")
                return
            }

            camera.getViewMatrix(viewMatrix,0)
            camera.getProjectionMatrix(projectMatrix,0,0.01f,10f)
            // Temporarily render here
            drawPoint()
            drawLine(null,anchorList,viewMatrix,projectMatrix)

            val pointX = width/2F
            val pointY = height/2F
            val hitResults =frame.hitTest(pointX,pointY) // Detection point is the center of the screen

            // Accuracy of anchor point is uncertain
            if (hitResults.isNotEmpty() and (hitResults.size>0)){
                val hitResult = hitResults.last()
                val type = trackable(hitResult.trackable) // This step only prints the trackable of hitResult, with no practical significance

                val trackable = hitResult.trackable

//                if ((trackable is Plane ) && trackable.isPoseInPolygon(hitResult.hitPose)){ // Tracking type is Plane and anchor is on the plane - detection is too strict
                if((trackable is Plane ) or (trackable is Point) or (trackable is DepthPoint )){// Tracking type can be Plane, Point, or DepthPoint
                    val anchor : Anchor
                    try {
                        anchor = hitResult.createAnchor()

                        if (anchor.trackingState == TrackingState.TRACKING){
                            detectSuccess("Anchor: $type ${anchor.trackingState.name} ")
                            // Get the position of the point
                            val pose = FloatArray(16)
                            anchor.pose.toMatrix(pose ,0)

                            // Render Bitmap (circle bitmap)
                            bitmapRenderer.upDateMatrix(pose,viewMatrix,projectMatrix)
                            bitmapRenderer.onDrawFrame()

                            // Add anchor point
                            addAnchorPoint(anchor)
                            Log.e(TAG,"bitmapRenderer.onDrawFrame():${hitResult.distance}")
                        }else{
                           detectFailed(anchor.trackingState.name)
                        }

                        // Temporarily render here
                        drawPoint(true)
                        drawLine(anchor,anchorList,viewMatrix,projectMatrix)
                    }catch (e:Exception){
                        Log.e(TAG,"Exception:$e")
                    }
                }
                else{
                    detectFailed(trackable(hitResult.trackable))
                }
            }else{
                detectFailed("Move your phone to detect surface features")
                return
            }

            Log.w(TAG,"Elapsed: ${System.currentTimeMillis()-tt}ms")
        }
    }

    private fun trackable(trackable: Trackable):String{
        val msg = when (trackable) {
            is Point -> "Point"
            is Plane -> "Plane"
            is InstantPlacementPoint -> "InstantPlacementPoint"
            is Earth -> "Earth"
            is DepthPoint -> "DepthPoint"
            is AugmentedFace -> "AugmentedFace"
            is AugmentedImage -> "AugmentedImage"
            else -> "Other"
        }
        Log.e(TAG,"trackable is $msg")

        return msg
    }

    fun add(){
        if (detectPointOrPlane){
            anchorQueue.clear()
            anchorQueue.add(motionEvent)
            Log.w(TAG,"add: ${anchorQueue.size}")
        }
    }

    @Synchronized
    private fun addAnchorPoint(anchor: Anchor){
        anchorQueue.poll()?.run {
            anchorList.takeIf {anchorList.size==limitsSize }?.apply {
                anchorList.first().detach()
                anchorList.removeFirst()
                anchorList.first().detach()
                anchorList.removeFirst()
            }
            anchorList.add(anchor)
        }
    }
    @Synchronized
    fun delete(){
        if (detectPointOrPlane && anchorList.isNotEmpty()){
            if (anchorList.size%2==0){
                Log.w(TAG,"delete:1 ${anchorList.size}")
                anchorList.last().detach()
                Log.w(TAG,"delete:2 ${anchorList.size}")
                anchorList.removeLast()
                Log.w(TAG,"delete:3 ${anchorList.size}")
                anchorList.last().detach()
                Log.w(TAG,"delete:4 ${anchorList.size}")
                anchorList.removeLast()
                Log.w(TAG,"delete:5 ${anchorList.size}")
            }else{
                Log.w(TAG,"delete:6 ${anchorList.size}")
                anchorList.last().detach()
                Log.w(TAG,"delete:7 ${anchorList.size}")
                anchorList.removeLast()
                Log.w(TAG,"delete:8 ${anchorList.size}")
            }

            Log.w(TAG,"delete: ${anchorList.size}")
        }
    }


    /**
     * Draw line
     * @param currentAnchor current anchor point
     * @param list anchor point list
     * @param view view matrix
     * @param project projection matrix
     */
    @Synchronized
    private fun drawLine(currentAnchor: Anchor?, list:ArrayList<Anchor>, view:FloatArray, project:FloatArray){
            val size = list.size / 2
            for (index in 0 until size){
                // Both points have the same effect, so commented out
                val pose1 = list[index*2].pose.translation
                val pose2 = list[index*2+1].pose.translation
                val point1 = floatArrayOf(
                    pose1[0],pose1[1],pose1[2],
                    pose2[0],pose2[1],pose2[2],
                )
                lineRenderer.vertexBuffer.put(point1).position(0)
                lineRenderer.upDateMatrix(view,project)
                lineRenderer.onDrawFrame()

                drawPicture(list[index*2].pose, list[index*2+1].pose,view,project)
            }

        // Render when current anchor is not null
        currentAnchor?.run {
            val isAnchor = list.isNotEmpty() && list.size % 2 != 0
            if (isAnchor){
                val anchor = list.last()
                val pose1 = anchor.pose
                val pose2 = this.pose
                var data = floatArrayOf(
                    pose1.tx(),pose1.ty(),pose1.tz(),
                    pose2.tx(),pose2.ty(),pose2.tz()
                )

                lineRenderer.vertexBuffer.put(data).position(0)
                lineRenderer.upDateMatrix(view,project)
                lineRenderer.onDrawFrame()

                drawPicture(pose1,pose2,view,project)
            }
        }
    }

    /**
     * Draw point
     * @param draw whether to draw the single point when the number of points is odd
     */
    @Synchronized
    private fun drawPoint(draw:Boolean = false){
        if (anchorList.isNullOrEmpty()){
            return
        }
        val remainder = anchorList.size % 2
        var size = anchorList.size - 1
        if (!draw && remainder!=0){
            size -= 1
        }

        for (i in 0..size){
            val pose = FloatArray(16)
            val anchor = anchorList[i]
            anchor.pose.toMatrix(pose ,0)
            pointRenderer.upDateMatrix(pose,viewMatrix,projectMatrix)
            pointRenderer.onDrawFrame()
        }
    }

    @Synchronized
    fun drawPicture(pose1:Pose,pose2:Pose,view: FloatArray,project: FloatArray){
        // Calculate the distance between two points
        val length = pictureRenderer.length(pose1,pose2)
        val res = String.format("%.2f", length)
        // Get the bitmap to be drawn

        pictureRenderer.setLength2Bitmap("${res}m")
        // Update vertex coordinates
        pictureRenderer.upDataVertex(pose1,pose2,view)
        // Update MVP matrix and draw
        pictureRenderer.upDatePMatrix(project)
        pictureRenderer.onDrawFrame()
    }


    private fun detectSuccess(msg:String = "Detection successful"){
        detectPointOrPlane=true
        iViewInterface?.detectSuccess(msg)
    }

    private fun detectFailed(msg:String = "Detection failed"){
        detectPointOrPlane=false
        iViewInterface?.detectFailed(msg)
    }
}