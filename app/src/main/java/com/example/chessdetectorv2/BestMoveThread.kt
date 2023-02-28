package com.example.chessdetectorv2

import android.content.ContentValues.TAG
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.example.cameraxapp.MyApplication
import kotlinx.coroutines.Runnable
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.math.roundToInt

class BestMoveThread(
    textView: TextView,
    evaluationView: TextView,
    progressBar: ProgressBar,
    imageViewBoardEval : ImageView,
    fullFen: String,
    process: Process
) : Thread() {

    val textView: TextView
    val imageViewBoardEval : ImageView
    val evaluationView: TextView
    var progressBar: ProgressBar
    val fullFen: String
    val process: Process
    var inStockfish: BufferedReader
    var outStockfish: BufferedWriter
    val isReady = "isready\n"
    val position = "position fen "
    val go = "go movetime 30000\n"
    val stop = "stop\n"

    init {
        this.textView = textView
        this.evaluationView = evaluationView
        this.fullFen = fullFen
        this.process = process
        this.progressBar = progressBar
        this.imageViewBoardEval = imageViewBoardEval
        inStockfish = BufferedReader(InputStreamReader(process.inputStream))
        outStockfish = BufferedWriter(OutputStreamWriter(process.outputStream))

    }


    override fun run() {
        try {
            Log.d(TAG, "Thread avviato")

            outStockfish.write(isReady)
            outStockfish.flush();
            var line = inStockfish.readLine()
            while (line != "readyok") {
                line = inStockfish.readLine()
                Log.d(TAG, line)
            }
            outStockfish.write(position + fullFen + "\n")
            outStockfish.flush();
            Log.d(TAG, position + fullFen + "\n")
            outStockfish.write(go + "\n")
            outStockfish.flush();
            Log.d(TAG, go + "\n")

            line = inStockfish.readLine()
            while (line != null && !line.startsWith("bestmove") && !interrupted()) {
                val listString = line.split(" ")
                Log.d(TAG, listString.toString())
                for (i in 0 until listString.size - 1) {
                    if (listString[i] == "score") {
                        if (listString[i + 1] == "cp") {
                            evaluationView.post(Runnable {
                                run {
                                    evaluationView.text =
                                        "Eval: ${listString[i + 2].toFloat() / 100}"
                                    progressBar.progress =
                                        (listString[i + 2].toFloat() / 100).roundToInt()
                                }
                            })


                        }
                        if (listString[i + 1] == "mate") {
                            evaluationView.post(Runnable {
                                run {
                                    evaluationView.text = "Eval: mate in ${listString[i + 2]}"
                                    if (listString[i + 2].toInt() > 0) {
                                        progressBar.progress = 15
                                    } else {
                                        progressBar.progress = 15

                                    }
                                }
                            })
                        }

                    }
                    if (listString[i] == "pv") {
                        val bestMove = listString[i + 1]
                        textView.post(Runnable {
                            run {
                                textView.text = "Best move: ${bestMove}"
                                imageViewBoardEval.setImageBitmap(MyApplication.chessboard.drawBestMove(bestMove))
                            }
                        })
                    }
                }
                line = inStockfish.readLine()

            }
            if (line == null) {
                textView.post(Runnable {
                    run {
                        textView.text = "Position not valid"
                    }
                })
            }else{
                outStockfish.write(stop)
                outStockfish.flush()
                Log.d(TAG, stop)
                Log.d(TAG, line)
                textView.post(Runnable {
                    run {
                        if(line.startsWith("bestmove")){
                            textView.text = "Best move: ${line.split(" ")[1]}"

                        }else{
                            textView.text = "Best move: ${line}"

                        }
                    }
                })
            }

        } catch (i: InterruptedException) {
            outStockfish.write(stop)
            outStockfish.flush();
            textView.post(Runnable {
                run {
                    textView.text = "Best move:"
                }
            })
            evaluationView.post(Runnable {
                run {
                    evaluationView.text = "Eval:"

                }
            })

        } catch (e: Exception) {
            Log.d(TAG, e.toString())

        }

    }
}


