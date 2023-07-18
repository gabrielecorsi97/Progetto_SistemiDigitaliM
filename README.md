# Sviluppo di una applicazione per il riconoscimento di una posizione durante una partita di scacchi

Il progetto ha l'obiettivo di implementare in un dispositivo embedded un software capace grazie ad una foto di specificare 
la posizione corrente di una partita di scacchi, restituendo all'utente la prossima mossa migliore e la notazione FEN.

Per tutti i dettagli è possibile visualizzare [il report del progetto](https://github.com/gabrielecorsi97/progetto_SistemiDigitaliM/blob/master/RelazioneSistemiDigitaliM_GabrieleCorsi.pdf).

I problemi affiorati e risolti durante lo svolgimento sono stati principalmente due:
  - individuare in modo efficace la scacchiera all'interno di una immagine (risolto con l'utilizzo della trasformata di Hough)
  - individuare, classificare e posizionare i pezzi all'interno della scacchiera (risolto con l'utilizzo di una rete neurale convoluzionale, YoloV5)

## Demo 
<img src="https://github.com/gabrielecorsi97/progetto_SistemiDigitaliM/blob/master/images/demo_relazione_cut.gif.gif"  height="400">  


## Individuazione scacchiera

Per l'individuazione della scacchiera è stato creato un algoritmo basato sulla trasformata di Hough, in particolare è stato necessario essere in grado 
di eliminare eventuali outliers e fare in modo che tutto il procedimento sia abbastanza efficiente in modo da poter essere eseguito in tempo reale su uno smartphone.

<img src="https://github.com/gabrielecorsi97/progetto_SistemiDigitaliM/blob/master/images/hough_transform3.png"  height="400">  

## Individuazione pezzi

Per l'individuazione dei pezzi si è utilizzata una rete neurale convoluzionale, in particolare la rete YoloV5. Dopo essere stata addestrata è stata implementata 
nell'applicazione Android sfruttando la libreria Pytorch.  

<img src="https://github.com/gabrielecorsi97/progetto_SistemiDigitaliM/blob/master/images/input_image_inference.png"  height="400">

## Librerie utilizzate

  - opnecv 4.5.1
  - scikit-image 0.18.3
  - numpy 1.19.5
  - scipy 1.4.1
  - pytorch
  - chaquopy
