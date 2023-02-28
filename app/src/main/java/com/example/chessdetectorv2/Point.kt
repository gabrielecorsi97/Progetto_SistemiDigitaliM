package com.example.chessdetectorv2

class Point(x: Int, y: Int){
    var x : Int
    var y : Int

    init{
        this.x = x
        this.y = y
    }

    override fun toString() : String{
        return "Point($x,$y)"
    }
}