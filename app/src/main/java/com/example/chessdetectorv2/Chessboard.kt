package com.example.chessdetectorv2

import android.content.ContentValues.TAG
import android.content.res.AssetManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.example.cameraxapp.MyApplication
import com.example.chessdetectorv2.Utils.rotateBitmap
import kotlin.math.roundToInt

class Chessboard  constructor(private val classes: Array<String>, private val assetManager: AssetManager){
    var pieces : List<Piece> = emptyList()
    private val chessBoardPng : Bitmap
    private var piecesDrawable : Array<Bitmap>
    lateinit var currentBoardWithPiece : Bitmap
    lateinit var leftPointOfPieces : Array<Point>
    lateinit var rightPointOfPieces : Array<Point>
    lateinit var upPointOfPieces : Array<Point>
    lateinit var downPointOfPieces : Array<Point>

    lateinit var leftPointNew : Array<Point>
    lateinit var rightPointNew : Array<Point>
    lateinit var upPointNew : Array<Point>
    lateinit var downPointNew : Array<Point>
    var pointsAreUpdate : Boolean = false
    private val columns = "abcdefgh"
    var rotation : Int


    lateinit var cellDetected : Bitmap
    var arrow : Bitmap
    var fen = ""
    var fullFen = ""
    var firstPlayer = "White"
    private val CHESSBOARD_CELL_LENGHT = 80
    private val pieceFenNotation = mapOf(1 to "b", 2 to "k", 3 to "n",
                                                      4 to "p", 5 to "q", 6 to "r",
                                                      7 to "B", 8 to "K", 9 to "N",
                                                      10 to "P", 11 to "Q", 12 to "R")

    init {
        val chessBoardPng = Drawable.createFromStream(this.assetManager.open("chessboardempty.png"), null)!!.toBitmap(640,640,Bitmap.Config.ARGB_8888)
        this.chessBoardPng = chessBoardPng
        val piecesDrawable  = Array(classes.size){ i -> Drawable.createFromStream(this.assetManager.open("${classes[i]}.png"), null)!!
            .toBitmap(80,80, Bitmap.Config.ARGB_8888)}
        this.piecesDrawable = piecesDrawable
        arrow = Drawable.createFromStream(this.assetManager.open("arrow.png"), null)!!.toBitmap(80,80, Bitmap.Config.ARGB_8888)
        rotation = 0
    }

    companion object : SingletonHolder<Chessboard,Array<String>, AssetManager>(::Chessboard)

    fun add90DegreeRotation(){
        if(rotation == 270){
            rotation = 0
        }else{
            rotation += 90
        }
        try{
            currentPositionInFen()
        }catch (e : java.lang.Exception){

        }
    }

    fun addPieces(pieces: List<Piece>, test : Boolean=false){
        val lists = Array(12){ mutableListOf<Piece>()}
        val maxNum = arrayOf(2,1,2,8,1,2,2,1,2,8,1,2)
        for(piece in pieces){
            lists[piece.pClass].add(piece)
        }
        if(pieces.isEmpty()) return

        if(!test){
            if( lists[1].size == 0){
                var max = 0f
                var listToRemove = firstNonEmptyList(lists)
                var newBlackKing = listToRemove[0]
                for( i in 0 until lists.size){ // ciclo tra tutte le classi
                    if(i != 7){                     // esclusa la classe del re bianco
                        for(j in 1 until lists[i].size){ // ciclo tra tutti i pezzi di una classe
                            for(k in 0 until lists[i][j].classes_scores.size){ // ciclo tra tutti i punteggi di un pezzo
                                if(lists[i][j].classes_scores[k].first == 1 && lists[i][j].classes_scores[k].second > max){
                                    max = lists[i][j].classes_scores[k].second
                                    newBlackKing = lists[i][j]
                                    listToRemove = lists[i]
                                }
                            }
                        }
                    }

                }
                listToRemove.remove(newBlackKing)
                newBlackKing.pClass = 1
                newBlackKing.classes_scores[0] = Pair(1, max)
                lists[1].add(newBlackKing)
            }
            if( lists[7].size == 0){
                var max = 0f
                var listToRemove = firstNonEmptyList(lists)
                var newWhiteKing = listToRemove[0]

                for( i in 0 until lists.size){ // ciclo tra tutte le classi
                    if(i != 1){
                        for(j in 1 until lists[i].size){ // ciclo tra tutti i pezzi di una classe
                            for(k in 0 until lists[i][j].classes_scores.size){ // ciclo tra tutti i punteggi di un pezzo
                                if(lists[i][j].classes_scores[k].first == 7 && lists[i][j].classes_scores[k].second > max){
                                    max = lists[i][j].classes_scores[k].second
                                    newWhiteKing = lists[i][j]
                                    listToRemove = lists[i]
                                }
                            }
                        }
                    }

                }
                listToRemove.remove(newWhiteKing)
                newWhiteKing.pClass = 7
                newWhiteKing.classes_scores[0] = Pair(7, max)
                lists[7].add(newWhiteKing)
            }

            for( i in 0 until 12){
                if(lists[i].size > maxNum[i]){
                    filterPiece(lists[i], maxNum, lists)
                }
            }
        }



        this.pieces = pieces

    }

    private fun firstNonEmptyList(lists: Array<MutableList<Piece>>): MutableList<Piece> {
        for(i in 0 until lists.size){
            if(lists[i].isNotEmpty()) return lists[i]
        }
        return lists[0]
    }

    fun filterPiece(listPieces : MutableList<Piece>,maxNum : Array<Int>, lists : Array<MutableList<Piece>>){
        Log.d(TAG, "Sono stati trovati ${listPieces.size} di classe ${listPieces[0].pClass} (max=${maxNum[listPieces[0].pClass]})")
        Log.d(TAG, listPieces.toString())
        listPieces.sortWith ( compareByDescending {  it.classes_scores[0].second })
        Log.d(TAG, listPieces.toString())
        val toRemove = mutableListOf<Piece>()
        for(i in maxNum[listPieces[0].pClass] until listPieces.size ){   // ciclo sui pezzi "di troppo" di una classe
            for(j in 1 until listPieces[i].classes_scores.size){ // ciclo su tutti le "seconde scelte" di quel pezzo
                if(lists[listPieces[i].classes_scores[j].first].size<maxNum[listPieces[i].classes_scores[j].first]){
                    Log.d(TAG, "Pezzo  ${listPieces[i].classes_scores[0].first} con score ${listPieces[i].classes_scores[0].second} sostituito con ${listPieces[i].classes_scores[j].first} con score ${listPieces[i].classes_scores[j].second}")
                    listPieces[i].pClass = listPieces[i].classes_scores[j].first
                    listPieces[i].classes_scores[0] = listPieces[i].classes_scores[j]
                    lists[listPieces[i].pClass].add(listPieces[i])
                    toRemove.add(listPieces[i])
                    break
                }else{
                    Log.d(TAG, "Pezzo  ${listPieces[i].classes_scores[0].first} con score ${listPieces[i].classes_scores[0].second} NON sostituito con ${listPieces[i].classes_scores[j].first} con score ${listPieces[i].classes_scores[j].second}")
                }
            }
        }
        for(piece in toRemove){
            lists[listPieces[0].pClass].remove(piece)
        }
    }

    fun returnChessBoardWithPieces(): Bitmap{
        val copy = chessBoardPng.copy(chessBoardPng.config, true)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val canvas = Canvas(copy)
        for(piece in pieces){
            val (coorx,coory) = chessboardPieceCoordinate(piece)
            val x = (coorx*CHESSBOARD_CELL_LENGHT).toFloat()
            val y = (coory*CHESSBOARD_CELL_LENGHT).toFloat()
            canvas.drawBitmap(piecesDrawable[piece.pClass], x, y, paint)
        }
        currentBoardWithPiece = copy
        return copy

    }

    fun drawBestMove(bestMove: String) : Bitmap{
        var columStart = columns.indexOf(bestMove[0])
        var rankStart = bestMove[1].digitToInt()-1
        var columEnd = columns.indexOf(bestMove[2])
        var rankEnd = bestMove[3].digitToInt()-1
        val currentBoardWithBestMove = currentBoardWithPiece.copy(currentBoardWithPiece.config, true)
        val mPaintRectangle = Paint()
        val canvas = Canvas(currentBoardWithBestMove)
        val p = Paint().also { it.strokeWidth = 2.5F; it.color = Color.CYAN }

        mPaintRectangle.strokeWidth = 3.5F
        mPaintRectangle.style = Paint.Style.STROKE
        mPaintRectangle.color = Color.rgb(0,255,0)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        when(MyApplication.chessboard.rotation){
            0->{
                rankStart = 7- rankStart
                rankEnd = 7-rankEnd
                val rectStart = Rect(columStart*80, rankStart*80,columStart*80+80, rankStart*80+80 )
                val rectEnd = Rect(columEnd*80, rankEnd*80,columEnd*80+80, rankEnd*80+80 )
                canvas.drawRect(rectStart, mPaintRectangle)
                canvas.drawRect(rectEnd, mPaintRectangle)
                canvas.drawLine(
                    (columStart*80+40).toFloat(),
                    (rankStart*80+40).toFloat(),
                    (columEnd*80+40).toFloat(),
                    (rankEnd*80+40).toFloat(),
                    p)
                var angolo = Utils.getAngle( (columStart*80+40).toFloat(),(rankStart*80+40).toFloat(),(columEnd*80+40).toFloat(),(rankEnd*80+40).toFloat())
                if(columStart-columEnd>0){
                    angolo += 180
                }
                Log.d(TAG, "Angolo $angolo")
                var newArrow = arrow.rotateBitmap(angolo)
                when(angolo){
                    -45f,45f,225f,135f ->{
                        canvas.drawBitmap(newArrow, (columEnd*80-18).toFloat(),(rankEnd*80-18).toFloat(), paint)
                    }
                    -90f,90f,270f,-270f ->{
                        canvas.drawBitmap(newArrow, (columEnd*80).toFloat(),(rankEnd*80).toFloat(), paint)
                    }
                    0f,180f ->{
                        canvas.drawBitmap(newArrow, (columEnd*80-20).toFloat(),(rankEnd*80-20).toFloat(), paint)
                    }
                    -26.56505f,153.43495f,26.56505f, 206.56505f,243.43495f,63.43495f  ->{
                        canvas.drawBitmap(newArrow, (columEnd*80-15).toFloat(),(rankEnd*80-15).toFloat(), paint)
                    }
                }
            }
            90->{
                val rectStart = Rect(rankStart*80, columStart*80,rankStart*80+80, columStart*80+80 )
                val rectEnd = Rect(rankEnd*80, columEnd*80,rankEnd*80+80, columEnd*80+80 )
                canvas.drawRect(rectStart, mPaintRectangle)
                canvas.drawRect(rectEnd, mPaintRectangle)
                canvas.drawLine(
                    (rankStart*80+40).toFloat(),
                    (columStart*80+40).toFloat(),
                    (rankEnd*80+40).toFloat(),
                    (columEnd*80+40).toFloat(),
                    p)
                var angolo = Utils.getAngle( (rankStart*80+40).toFloat(),(columStart*80+40).toFloat(),(rankEnd*80+40).toFloat(),(columEnd*80+40).toFloat())
                if(rankStart-rankEnd>0){
                    angolo += 180
                }
                Log.d(TAG, "Angolo $angolo")

                var newArrow = arrow.rotateBitmap(angolo)
                when(angolo){
                    -45f,45f,225f,135f ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80-18).toFloat(),(columEnd*80-18).toFloat(), paint)
                    }
                    -90f,90f,270f,-270f ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80).toFloat(),(columEnd*80).toFloat(), paint)
                    }
                    0f,180f ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80).toFloat(),(columEnd*80).toFloat(), paint)
                    }
                    -26.56505f,153.43495f,26.56505f, 206.56505f,243.43495f,63.43495f   ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80-15).toFloat(),(columEnd*80-15).toFloat(), paint)
                    }
                }
            }
            180-> {
                columStart = 7 - columStart
                columEnd = 7 - columEnd
                val rectStart = Rect(columStart*80, rankStart*80,columStart*80+80, rankStart*80+80 )
                val rectEnd = Rect(columEnd*80, rankEnd*80,columEnd*80+80, rankEnd*80+80 )
                canvas.drawRect(rectStart, mPaintRectangle)
                canvas.drawRect(rectEnd, mPaintRectangle)
                canvas.drawLine(
                    (columStart*80+40).toFloat(),
                    (rankStart*80+40).toFloat(),
                    (columEnd*80+40).toFloat(),
                    (rankEnd*80+40).toFloat(),
                    p)
                var angolo = Utils.getAngle( (columStart*80+40).toFloat(),(rankStart*80+40).toFloat(),(columEnd*80+40).toFloat(),(rankEnd*80+40).toFloat())
                if(columStart-columEnd>0){
                    angolo += 180
                }
                Log.d(TAG, "Angolo $angolo")
                var newArrow = arrow.rotateBitmap(angolo)
                when(angolo){
                    -45f,45f,225f,135f ->{
                        canvas.drawBitmap(newArrow, (columEnd*80-18).toFloat(),(rankEnd*80-18).toFloat(), paint)
                    }
                    -90f,90f,270f,-270f ->{
                        canvas.drawBitmap(newArrow, (columEnd*80).toFloat(),(rankEnd*80).toFloat(), paint)
                    }
                    0f,180f ->{
                        canvas.drawBitmap(newArrow, (columEnd*80-20).toFloat(),(rankEnd*80-20).toFloat(), paint)
                    }
                    -26.56505f,153.43495f,26.56505f, 206.56505f,243.43495f,63.43495f  ->{
                        canvas.drawBitmap(newArrow, (columEnd*80-15).toFloat(),(rankEnd*80-15).toFloat(), paint)
                    }
                }
            }
            270->{
                rankStart = 7-rankStart
                rankEnd = 7 - rankEnd
                columStart = 7 - columStart
                columEnd = 7 - columEnd
                val rectStart = Rect(rankStart*80, columStart*80,rankStart*80+80, columStart*80+80 )
                val rectEnd = Rect(rankEnd*80, columEnd*80,rankEnd*80+80, columEnd*80+80 )
                canvas.drawRect(rectStart, mPaintRectangle)
                canvas.drawRect(rectEnd, mPaintRectangle)
                canvas.drawLine(
                    (rankStart*80+40).toFloat(),
                    (columStart*80+40).toFloat(),
                    (rankEnd*80+40).toFloat(),
                    (columEnd*80+40).toFloat(),
                    p)
                var angolo = Utils.getAngle( (rankStart*80+40).toFloat(),(columStart*80+40).toFloat(),(rankEnd*80+40).toFloat(),(columEnd*80+40).toFloat())
                if(rankStart-rankEnd>0){
                    angolo += 180
                }
                Log.d(TAG, "Angolo $angolo")
                var newArrow = arrow.rotateBitmap(angolo)
                when(angolo){
                    -45f,45f,225f,135f ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80-18).toFloat(),(columEnd*80-18).toFloat(), paint)
                    }
                    -90f,90f,270f,-270f ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80).toFloat(),(columEnd*80).toFloat(), paint)
                    }
                    0f,180f ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80-20).toFloat(),(columEnd*80-20).toFloat(), paint)
                    }
                    -26.56505f,153.43495f,26.56505f, 206.56505f,243.43495f,63.43495f  ->{
                        canvas.drawBitmap(newArrow, (rankEnd*80-15).toFloat(),(columEnd*80-15).toFloat(), paint)
                    }

                }

            }
        }


        return currentBoardWithBestMove
    }

    private fun chessboardPieceCoordinate(p: Piece) : Pair<Int,Int>{

        var verticalcoor = 0
        for( i in 0 until 9){
            val (m, b) = findLineEquation(leftPointOfPieces[i], rightPointOfPieces[i])
            val isUnder = pointIsUnderLine(p.position, m, b)  // mi fermo quando restituisco falso
            if(verticalcoor == 0 && !isUnder){
                Log.d(TAG, "Pezzo sopra la scacchiera!")
                return Pair(-1, -1)
            }
            if(!isUnder){
                break
            }
            verticalcoor += 1
        }
        if(verticalcoor == 9){
            print("Pezzo sotto la scacchiera!")
            return Pair(-1, -1)
        }
        var horizontalcoor = 0
        for(i in 0 until 9){
            val (m,b) = findLineEquation(downPointOfPieces[i], upPointOfPieces[i])
            val isRight = pointIsRightOfTheLine(p.position, m, b)
            if(horizontalcoor == 0 && !isRight){
                Log.d(TAG, "Pezzo a sinistra della scacchiera!")
                return Pair(-1, -1)
            }
            if(!isRight){
                break
            }
            horizontalcoor += 1
        }
        if(horizontalcoor == 9){
            Log.d(TAG, "Pezzo a destra della scacchiera!")
            return Pair(-1, -1)
        }

        return Pair(horizontalcoor-1, verticalcoor-1)
    }

    private fun findLineEquation(pointA : Point, pointB : Point) : Pair<Float,Float>{

        return try{
            val m = (((pointA.y-pointB.y).toFloat())/(pointA.x-pointB.x))
            var b = pointA.y - m*pointA.x
            if(m.isInfinite()){
                b=pointA.x.toFloat()
            }
            Pair(m, b)

        }catch (e : Exception){
            println("****\nCi entro mai qui????\n***")
            val m = Float.POSITIVE_INFINITY
            val b = pointA.x.toFloat()
            Pair(m, b)
        }
    }

    private fun pointIsUnderLine(p : Point, m : Float, b : Float) : Boolean{
        return (b<(p.y-m*p.x))
    }

    private fun pointIsRightOfTheLine(point : Point, m : Float, b : Float) : Boolean {
        return if(m.isInfinite()) {
            b < point.x
        } else {
            val x = (point.y-b)/m
            x<point.x
        }
    }

    fun drawPiecesOnImage(image : Bitmap): Bitmap{
        val copy = image.copy(chessBoardPng.config, true)
        val canvas = Canvas(copy)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for(piece in pieces){
            val centerx = ((piece.rect.right-piece.rect.left)/2F+piece.rect.left).roundToInt()
            val centery = ((piece.rect.top-piece.rect.bottom)/2F+piece.rect.bottom).roundToInt()
            val x = (centerx-CHESSBOARD_CELL_LENGHT/2).toFloat()
            val y = (centery-CHESSBOARD_CELL_LENGHT/2).toFloat()
            canvas.drawBitmap(piecesDrawable[piece.pClass], x, y, paint)
        }
        return copy
    }

    fun currentPositionInFen(): String {
        var res = ""
        var array = Array(8) { IntArray(8){0}}
        for(piece in pieces){
            val (coordx,coordy) = chessboardPieceCoordinate(piece)
            if(coordx>-1 && coordy>-1 && coordx<9 && coordy<9){
                array[coordy][coordx] = piece.pClass+1

            }
        }
        when(MyApplication.chessboard.rotation){
            0->{

            }
            90->{
                for(i in 0 until 8){ array[i].reverse() }
                array = transposeMatrix(array)
            }
            180-> {
                for(i in 0 until 8){ array[i].reverse() }
                array = transposeMatrix(array)
                for(i in 0 until 8){ array[i].reverse() }
                array = transposeMatrix(array)
            }
            270->{
                array = transposeMatrix(array)
                for(i in 0 until 8){ array[i].reverse() }
            }
        }
        Log.d(TAG, array.toString())
        for(rank in array){
            var blank = 0
            for(cell in rank){
                if(cell>0 && blank==0){
                    res += pieceFenNotation[cell]
                }else if(cell>0 && blank>0){
                    res = res +blank+ pieceFenNotation[cell]
                    blank = 0
                }else{
                    blank += 1
                }

            }
            if(blank!= 0){
                res += blank
            }
            res = "$res/"
        }
        res = res.dropLast(1)
        Log.d(TAG, res)
        setFenString(res)
        return res
    }

    fun setFenString(fen : String, whiteToMove : Boolean = true, whiteCanCastleLong : Boolean = false,
                     whiteCanCastleShort : Boolean = false, blackCanCastleLong: Boolean = false,
                     blackCanCastleShort: Boolean = false, enPassantPossible : String  = "", numHalfmove : Int = 1, numFullMove : Int = 1){
        val nextMove = if(whiteToMove) "w" else "b"
        val wCastleLong = if(whiteCanCastleLong) "Q" else ""
        val wCastleShort = if(whiteCanCastleShort) "K" else ""
        val bCastleLong = if(blackCanCastleLong) "q" else ""
        val bCastleShort = if(blackCanCastleShort) "k" else ""
        var castle = wCastleLong+wCastleShort+bCastleLong+bCastleShort
        castle = if((castle)!= "") castle else "-"
        val enpassant = if(enPassantPossible == "") "-" else enPassantPossible

        val fullFen = "$fen $nextMove $castle $enpassant $numHalfmove $numFullMove"
        this.fen = fen
        this.fullFen = fullFen
    }
    private fun transposeMatrix(matrix: Array<IntArray>): Array<IntArray> {
        val m = matrix.size
        val n = matrix[0].size
        val transposedMatrix = Array(n) { IntArray(m) }
        for (x in 0 until n) {
            for (y in 0 until m) {
                transposedMatrix[x][y] = matrix[y][x]
            }
        }
        return transposedMatrix
    }

}



open class SingletonHolder<out T: Any, in A,in B>(creator: (A,B) -> T) {
    private var creator: ((A,B) -> T)? = creator
    @Volatile private var instance: T? = null

    fun getInstance(arg: A, arg2: B): T {
        val checkInstance = instance
        if (checkInstance != null) {
            return checkInstance
        }

        return synchronized(this) {
            val checkInstanceAgain = instance
            if (checkInstanceAgain != null) {
                checkInstanceAgain
            } else {
                val created = creator!!(arg,arg2)
                instance = created
                creator = null
                created
            }
        }
    }
}