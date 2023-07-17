# Sviluppo di una applicazione per il riconoscimento di una posizione durante una partita di scacchi

Il progetto ha l'obiettivo di implementare in un dispositivo embedded un software capace grazie ad una foto di specificare 
la posizione corrente di una partita di scacchi, restituendo all'utente la prossima mossa migliore e la notazione FEN.

Per tutti i dettagli è possibile visualizzare [il report del progetto](https://github.com/gabrielecorsi97/progetto_SistemiDigitaliM/blob/master/RelazioneSistemiDigitaliM_GabrieleCorsi.pdf).

I problemi affiorati e risolti durante lo svolgimento sono stati principalmente due:
  - individuare in modo efficace la scacchiera all'interno di una immagine (risolto con l'utilizzo della trasformata di Hough)
  - individuare, classificare e posizionare i pezzi all'interno della scacchiera (risolto con l'utilizzo di una rete neurale convoluzionale, YoloV5)

## Demo 
<img src="https://github.com/gabrielecorsi97/progetto_SistemiDigitaliM/blob/master/images/demo_relazione_cut.gif.gif"  height="400">  
