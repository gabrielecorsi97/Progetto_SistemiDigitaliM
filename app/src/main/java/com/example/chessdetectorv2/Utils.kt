package com.example.chessdetectorv2

import android.graphics.*
import com.example.cameraxapp.*
import org.pytorch.IValue
import org.pytorch.torchvision.TensorImageUtils
import java.lang.Math.atan2
import java.lang.Math.round
import java.nio.ByteBuffer
import kotlin.math.roundToInt

object Utils{
    fun callPythonModule(data: ByteArray, width: Int, height: Int, checked: Boolean, res : Bitmap): Boolean {
        val resultByteArray =
            module.callAttr("main", data, width, height, checked)
                .toJava(ByteArray::class.java)
        val buffer = ByteBuffer.wrap(resultByteArray)
        val success =
            buffer.int // -1 = non sono stati trovati i punti, 1 = sono stati trovati i punti
        val scale = 640/480f
        if (success == 1) {
            val leftPoint = Array<Point>(9) { _ -> Point((buffer.int * scale).roundToInt(), (buffer.int * scale).roundToInt()) }
            val rightPoint = Array<Point>(9) { _ -> Point((buffer.int * scale).roundToInt(), (buffer.int * scale).roundToInt()) }
            val upPoint = Array<Point>(9) { _ -> Point((buffer.int * scale).roundToInt(), (buffer.int * scale).roundToInt()) }
            val downPoint = Array<Point>(9) { _ -> Point((buffer.int * scale).roundToInt(), (buffer.int * scale).roundToInt()) }

            MyApplication.chessboard.leftPointNew = leftPoint
            MyApplication.chessboard.rightPointNew = rightPoint
            MyApplication.chessboard.upPointNew = upPoint
            MyApplication.chessboard.downPointNew = downPoint
            MyApplication.chessboard.pointsAreUpdate = true
            var cellDetected = drawLinesVertical(
                MyApplication.chessboard.upPointNew,
                MyApplication.chessboard.downPointNew,
                res.copy(res.config, true)
            )
            cellDetected = drawLinesHorizontal(
                MyApplication.chessboard.leftPointNew,
                MyApplication.chessboard.rightPointNew,
                cellDetected
            )
            MyApplication.chessboard.cellDetected = cellDetected
            return true
        } else {
            return false
        }
    }

    fun rotateCutResizeBitmap(res: Bitmap): Bitmap {
        var res = res.rotateBitmap(90F)
        res = Bitmap.createBitmap(res, 0, (res.height - res.width) / 2, res.width, res.width)
        res = Bitmap.createScaledBitmap(res, 640, 640, true)
        return res
    }

    fun Bitmap.rotateBitmap(angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
    }

    fun inferenceYoloAndDrawPieceOnRes(res: Bitmap, strong: Boolean =false): Bitmap {
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            res,
            PrePostProcessor.NO_MEAN_RGB,
            PrePostProcessor.NO_STD_RGB
        )

        val outputs = if(strong){
            modulePytorchStrong.forward(IValue.from(inputTensor)).toTuple()[0].toTensor().dataAsFloatArray
        }else{
            modulePytorch.forward(IValue.from(inputTensor)).toTuple()[0].toTensor().dataAsFloatArray

        }

        val results = PrePostProcessor.outputsToNMSPredictions(outputs, 1F, 1F, 1F, 1F, 0F, 0F)
        val listPieces = List<Piece>(results.size) { i ->
            Piece(
                results[i].classIndex,
                results[i].score,
                results[i].rect,
                results[i].classes_scores_list
            )
        }
        MyApplication.chessboard.addPieces(listPieces)

        return MyApplication.chessboard.drawPiecesOnImage(res)
    }

    fun drawRectOnPieces(pieces: List<Piece>, rotatedBitmap: Bitmap): Bitmap {
        val TEXT_X = 5
        val TEXT_Y = 12
        val TEXT_WIDTH = 110
        val TEXT_HEIGHT = 15
        val bitmapWithRect = rotatedBitmap.copy(rotatedBitmap.config, true)
        val mPaintRectangle = Paint()
        val mPaintText = Paint()
        val canvas = Canvas(bitmapWithRect)
        for (piece in pieces) {
            mPaintRectangle.strokeWidth = 2F
            mPaintRectangle.style = Paint.Style.STROKE
            canvas.drawRect(piece.rect, mPaintRectangle)
            val mPath = Path()
            val mRectF = RectF(
                piece.rect.left.toFloat(),
                piece.rect.top.toFloat(),
                (piece.rect.left + TEXT_WIDTH).toFloat(),
                (piece.rect.top + TEXT_HEIGHT).toFloat()
            )
            mPath.addRect(mRectF, Path.Direction.CW)
            mPaintText.color = Color.argb(120,255,39,150 )
            canvas.drawPath(mPath, mPaintText)
            mPaintText.color = Color.WHITE
            mPaintText.strokeWidth = 0F
            mPaintText.style = Paint.Style.FILL
            mPaintText.textSize = 14F
            canvas.drawText(
                String.format(
                    "%s %.2f",
                    PrePostProcessor.mClasses[piece.pClass],
                    piece.score
                ),
                (piece.rect.left + TEXT_X).toFloat(),
                (piece.rect.top + TEXT_Y).toFloat(),
                mPaintText
            )
            val x = (piece.rect.right - piece.rect.left) / 2F + piece.rect.left
            val y = piece.rect.bottom - 5F
            canvas.drawPoint(x, y, mPaintRectangle)
        }
        return bitmapWithRect

    }


    fun drawLinesVertical(upPoint: Array<Point>, downPoint: Array<Point>, res: Bitmap): Bitmap {
        val c = Canvas(res)
        val p = Paint().also { it.strokeWidth = 2.5F }
        p.color = Color.CYAN
        for (i in upPoint.indices) {
            c.drawLine(
                upPoint[i].x.toFloat(),
                upPoint[i].y.toFloat(),
                downPoint[i].x.toFloat(),
                downPoint[i].y.toFloat(),
                p
            )
        }
        return res
    }

    fun drawLinesHorizontal(leftPoint: Array<Point>, rightPoint: Array<Point>, res: Bitmap): Bitmap {
        val c = Canvas(res)
        val p = Paint().also { it.strokeWidth = 2.5F }
        p.color = Color.CYAN

        for (i in leftPoint.indices) {
            c.drawLine(
                leftPoint[i].x.toFloat(),
                leftPoint[i].y.toFloat(),
                rightPoint[i].x.toFloat(),
                rightPoint[i].y.toFloat(),
                p
            )
        }
        return res
    }
    fun getAngle(point1x: Float, point1y: Float, point2x: Float, point2y: Float): Float{
        val m = (point2y-point1y)/(point2x-point1x)

        var theta_radians = kotlin.math.atan(m)
        return (theta_radians*180/kotlin.math.PI).toFloat()
    }
}

