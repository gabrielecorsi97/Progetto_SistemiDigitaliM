package com.example.cameraxapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.*
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.cameraxapp.databinding.ActivityEvaluationBinding
import com.example.chessdetectorv2.BestMoveThread
import java.io.*


class EvaluationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvaluationBinding
    private lateinit var thread: Thread
    private lateinit var process: Process
    private val TAG = "EvaluationActivity"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityEvaluationBinding.inflate(layoutInflater)
        process = startStockfish()

        thread = BestMoveThread(
            binding.bestMoveView,
            binding.evaluationView,
            binding.progressBar,
            binding.imageViewBoardEval,
            MyApplication.chessboard.fullFen,
            process
        )
        thread.start()
        Log.d(TAG, MyApplication.chessboard.fullFen)
        binding.fenView.text = "Fen: ${MyApplication.chessboard.fullFen}"

        binding.imageViewBoardEval.setImageBitmap(MyApplication.chessboard.currentBoardWithPiece)
        binding.buttonCopyFen.setOnClickListener {
            val clipboard: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val string = binding.fenView.text.split(" ", limit = 2)[1]
            val clip = ClipData.newPlainText("FEN", string)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Fen copied!" , Toast.LENGTH_SHORT).show();
        }
        when(MyApplication.chessboard.rotation){
            0->{
                binding.player0Eval.text = "White"
                binding.player180Eval.text = "Black"
                binding.player90Eval.text = ""
                binding.player270Eval.text = ""
            }
            90->{
                binding.player0Eval.text = ""
                binding.player180Eval.text = ""
                binding.player90Eval.text = "White"
                binding.player270Eval.text = "Black"
            }
            180-> {
                binding.player0Eval.text = "Black"
                binding.player180Eval.text = "White"
                binding.player90Eval.text = ""
                binding.player270Eval.text = ""
            }
            270->{
                binding.player0Eval.text = ""
                binding.player180Eval.text = ""
                binding.player90Eval.text = "Black"
                binding.player270Eval.text = "White"
            }
        }

        binding.buttonNextMove.setOnClickListener {
            updateUI()

        }

        binding.switchBlackCastleLong.setOnClickListener {
            updateUI()

        }

        binding.switchBlackCastleShort.setOnClickListener {
            updateUI()

        }

        binding.switchWhiteCastleLong.setOnClickListener {
            updateUI()

        }

        binding.switchWhiteCastleShort.setOnClickListener {
            updateUI()

        }
        binding.spinnerEnPassant2.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if((binding.spinnerEnPassant2.selectedItem.toString()!="-" && binding.spinnerEnPassant3.selectedItem.toString()!="-" ) ||
                    (binding.spinnerEnPassant2.selectedItem.toString()=="-" && binding.spinnerEnPassant3.selectedItem.toString()=="-")){
                    updateUI()
                }
            }

        }
        binding.spinnerEnPassant3.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if((binding.spinnerEnPassant2.selectedItem.toString()!="-" && binding.spinnerEnPassant3.selectedItem.toString()!="-" ) ||
                    (binding.spinnerEnPassant2.selectedItem.toString()=="-" && binding.spinnerEnPassant3.selectedItem.toString()=="-")){
                    updateUI()
                }
            }

        }

        setContentView(binding.root)
    }

    private fun startStockfish(): Process {
        val path = applicationContext.applicationInfo.nativeLibraryDir + "/stockfish.android.so"
        val file = File(path)
        val result = Runtime.getRuntime().exec(file.path)
        val br = BufferedReader(InputStreamReader(result.inputStream))
        var line = br.readLine()
        Log.d(TAG, "*****\n$line\n*****")
        while (line != "Stockfish 15.1 by the Stockfish developers (see AUTHORS file)") {
            Thread.sleep(500)
            line = br.readLine()
        }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPause(){
        super.onPause()
        thread.interrupt()
        thread.join()
        if(process.isAlive){
            val bw = BufferedWriter(OutputStreamWriter(process.outputStream))
            bw.write("quit")
            bw.flush()
            bw.close()
        }


    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateUI() {
        binding.progressBar2.visibility = VISIBLE
        binding.progressBar2.requestLayout()
        binding.progressBar2.postInvalidate()



            updateFen()
            if (thread.isAlive) {
                thread.interrupt()
                thread.join()
            }

            if(!process.isAlive){
                process = startStockfish()
            }
            thread = BestMoveThread(
                binding.bestMoveView,
                binding.evaluationView,
                binding.progressBar,
                binding.imageViewBoardEval,
                MyApplication.chessboard.fullFen,
                process
            )
            thread.start()



        binding.progressBar2.visibility = GONE
        binding.progressBar2.postInvalidate()

    }

    private fun updateFen() {
        val ep = binding.spinnerEnPassant2.selectedItem.toString()+binding.spinnerEnPassant3.selectedItem.toString()
        if(ep.contains("-")){
            MyApplication.chessboard.setFenString(
                MyApplication.chessboard.fen,
                binding.buttonNextMove.isChecked,
                binding.switchWhiteCastleLong.isChecked,
                binding.switchWhiteCastleShort.isChecked,
                binding.switchBlackCastleLong.isChecked,
                binding.switchBlackCastleShort.isChecked
                )
        }else{
            MyApplication.chessboard.setFenString(
                MyApplication.chessboard.fen,
                binding.buttonNextMove.isChecked,
                binding.switchWhiteCastleLong.isChecked,
                binding.switchWhiteCastleShort.isChecked,
                binding.switchBlackCastleLong.isChecked,
                binding.switchBlackCastleShort.isChecked,
                ep
                )
        }

        binding.fenView.text = "Fen: ${MyApplication.chessboard.fullFen}"
    }


}