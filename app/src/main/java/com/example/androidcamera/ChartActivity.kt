package com.example.androidcamera

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class ChartActivity : ComponentActivity() {

    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        // Inizializza il grafico
        lineChart = findViewById(R.id.lineChartR)

        // Crea i dati del grafico
        val entries = ArrayList<Entry>()
        // Aggiungi i tuoi dati qui
        entries.add(Entry(0f, 5f))
        entries.add(Entry(1f, 10f))
        entries.add(Entry(2f, 7f))
        entries.add(Entry(3f, 15f))

        // Crea un dataset e aggiungilo al grafico
        val dataSet = LineDataSet(entries, "Valori nel tempo")
        dataSet.color = Color.RED
        dataSet.setCircleColor(Color.BLUE)
        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Personalizza gli assi
        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(listOf("0s", "1s", "2s", "3s"))
        lineChart.axisLeft.axisMinimum = 0f // Minimo valore sull'asse Y

        // Nascondi la descrizione del grafico
        lineChart.description.isEnabled = false

        // Aggiorna il grafico
        lineChart.invalidate() // refresh del grafico
    }

    // Funzione per aggiornare il grafico con nuovi dati
    fun updateChart(newEntry: Entry) {
        val data = lineChart.data
        val dataSet = data.getDataSetByIndex(0) as LineDataSet
        dataSet.addEntry(newEntry)
        data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }
}