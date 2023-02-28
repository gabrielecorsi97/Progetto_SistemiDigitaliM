package com.example.chessdetectorv2

import android.graphics.Rect
import kotlin.math.roundToInt

class Piece(pieceIndex : Int, score: Float, position: Rect, classes_scores : MutableList<Pair<Int, Float>>) {
    var score: Float
    var pClass : Int
    var position : Point
    var rect: Rect
    var classes_scores : MutableList<Pair<Int, Float>>

    init{
        this.pClass = pieceIndex
        this.position =Point(((position.right-position.left)/2F+position.left).roundToInt(),(position.bottom - 10F).roundToInt())
        this.rect = position
        this.score = score
        this.classes_scores = classes_scores
    }

    override fun toString() : String{
        return "Piece(type: $pClass, score: $score, classes_scores:$classes_scores, pos: $position, rect: $rect, )"
    }
}