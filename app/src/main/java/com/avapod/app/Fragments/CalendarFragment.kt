package com.avapod.app.Fragments

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.avapod.app.R
import com.avapod.app.utils.StringUtils
import org.json.JSONObject
import saman.zamani.persiandate.PersianDate
import java.time.LocalDate
import java.time.chrono.HijrahDate

class CalendarFragment : Fragment() {

    private lateinit var txtMonthYear: TextView
    private lateinit var gridDays: GridLayout
    private lateinit var txtEvent: TextView
    private lateinit var txtShamsi: TextView
    private lateinit var txtMiladi: TextView
    private lateinit var txtGhamari: TextView

    private var currentYear = 0
    private var currentMonth = 0
    private var currentDay = 0
    private var selectedDay = 0
    private var selectedYear = 0
    private var selectedMonth = 0

    private var holidaysMap: MutableMap<String, String> = mutableMapOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        loadHolidaysFromJson()
        setCurrentDate()
        setupCalendar(currentYear, currentMonth)
        setupEvents(currentYear, currentMonth, currentDay)
        updateAllDates(currentYear, currentMonth, currentDay)
        setupNavigation(view)
        setupTodayButton(view)
    }

    private fun initViews(view: View) {
        txtMonthYear = view.findViewById(R.id.txt_month_year)
        gridDays = view.findViewById(R.id.grid_days)
        txtEvent = view.findViewById(R.id.txt_event)
        txtShamsi = view.findViewById(R.id.txt_shamsi)
        txtMiladi = view.findViewById(R.id.txt_miladi)
        txtGhamari = view.findViewById(R.id.txt_ghamari)
    }

    private fun loadHolidaysFromJson() {
        try {
            val now = PersianDate()
            val shamsiYear = now.getShYear()

            val jsonResId = when (shamsiYear) {
                1405 -> R.raw.holidays1405
                1406 -> R.raw.holidays1406
                1407 -> R.raw.holidays1407
                1408 -> R.raw.holidays1408
                1409 -> R.raw.holidays1409
                1410 -> R.raw.holidays1410
                else -> R.raw.holidays1405
            }

            val jsonString = resources.openRawResource(jsonResId).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val dataArray = jsonObject.getJSONArray("data")

            holidaysMap.clear()

            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val shamsiDate = item.getString("shamsiDate")
                val description = item.optString("holidayDesription", null)
                val isHoliday = item.getBoolean("isHoliday")

                val parts = shamsiDate.split("/")
                if (parts.size == 3) {
                    val month = parts[1].toInt()
                    val day = parts[2].toInt()
                    val key = "$month-$day"

                    if (!description.isNullOrEmpty() && description != "null") {
                        holidaysMap[key] = description
                    } else if (isHoliday) {
                        holidaysMap[key] = getString(R.string.event_holiday)
                    }
                }
            }
        } catch (e: Exception) {
            holidaysMap.clear()
        }
    }

    private fun setCurrentDate() {
        val now = PersianDate()
        currentYear = now.getShYear()
        currentMonth = now.getShMonth()
        currentDay = now.getShDay()
        selectedDay = -1
        selectedYear = -1
        selectedMonth = -1
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNavigation(view: View) {
        view.findViewById<View>(R.id.btn_prev_month)?.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear--
            }
            clearSelection()
            setupCalendar(currentYear, currentMonth)
            setupEvents(currentYear, currentMonth, currentDay)
            updateAllDates(currentYear, currentMonth, currentDay)
        }

        view.findViewById<View>(R.id.btn_next_month)?.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear++
            }
            clearSelection()
            setupCalendar(currentYear, currentMonth)
            setupEvents(currentYear, currentMonth, currentDay)
            updateAllDates(currentYear, currentMonth, currentDay)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupTodayButton(view: View) {
        view.findViewById<View>(R.id.btn_today)?.setOnClickListener {
            val today = PersianDate()
            currentYear = today.getShYear()
            currentMonth = today.getShMonth()
            currentDay = today.getShDay()
            clearSelection()
            setupCalendar(currentYear, currentMonth)
            setupEvents(currentYear, currentMonth, currentDay)
            updateAllDates(currentYear, currentMonth, currentDay)

        }
    }

    private fun clearSelection() {
        selectedDay = -1
        selectedYear = -1
        selectedMonth = -1
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateAllDates(year: Int, month: Int, day: Int) {
        try {
            txtShamsi.text = "${StringUtils.toPersianNumber(year.toString())}/${StringUtils.toPersianNumber(String.format("%02d", month))}/${StringUtils.toPersianNumber(String.format("%02d", day))}"

            val pdate = PersianDate()
            pdate.setShYear(year)
            pdate.setShMonth(month)
            pdate.setShDay(day)
            txtMiladi.text = "${pdate.getGrgYear()}/${String.format("%02d", pdate.getGrgMonth())}/${String.format("%02d", pdate.getGrgDay())}"

            val miladiYear = pdate.getGrgYear()
            val miladiMonth = pdate.getGrgMonth()
            val miladiDay = pdate.getGrgDay()

            val localDate = LocalDate.of(miladiYear, miladiMonth, miladiDay)
            val hijrahDate = HijrahDate.from(localDate)

            val ghamariYear = hijrahDate.get(java.time.temporal.ChronoField.YEAR)
            val ghamariMonth = hijrahDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
            val ghamariDay = hijrahDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)

            txtGhamari.text = "${StringUtils.toPersianNumber(ghamariYear.toString())}/${StringUtils.toPersianNumber(String.format("%02d", ghamariMonth))}/${StringUtils.toPersianNumber(String.format("%02d", ghamariDay))}"

        } catch (e: Exception) {
            txtMiladi.text = getString(R.string.error_date_conversion)
            txtGhamari.text = getString(R.string.error_date_conversion)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupCalendar(year: Int, month: Int) {
        val pdate = PersianDate()
        pdate.setShYear(year)
        pdate.setShMonth(month)
        pdate.setShDay(1)

        val monthName = pdate.monthName()
        val persianYear = StringUtils.toPersianNumber(year.toString())
        txtMonthYear.text = "$monthName $persianYear"

        val firstDayOfMonth = pdate.dayOfWeek()
        val daysInMonth = pdate.getMonthDays()

        gridDays.removeAllViews()

        val gridWidth = gridDays.width
        val screenWidth = resources.displayMetrics.widthPixels
        val padding = 16
        val cellSize = if (gridWidth > 0) (gridWidth - padding) / 7 else (screenWidth - padding - 48) / 7
        val cellSizeWithMargin = cellSize - 4

        val emptyDays = firstDayOfMonth
        for (i in 0 until emptyDays) {
            val emptyView = TextView(requireContext())
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = cellSizeWithMargin
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(2, 2, 2, 2)
            emptyView.layoutParams = params
            gridDays.addView(emptyView)
        }

        val typeface = ResourcesCompat.getFont(requireContext(), R.font.vazir_regular)

        for (day in 1..daysInMonth) {
            val dayView = TextView(requireContext())
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = cellSizeWithMargin
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(2, 2, 2, 2)
            dayView.layoutParams = params

            dayView.gravity = android.view.Gravity.CENTER
            dayView.text = StringUtils.toPersianNumber(day.toString())
            dayView.setTextColor(Color.WHITE)
            dayView.textSize = 15f
            dayView.typeface = typeface
            dayView.isClickable = true
            dayView.isFocusable = true

            val isToday = (day == currentDay && year == currentYear && month == currentMonth)
            val isSelected = (selectedDay != -1 &&
                    day == selectedDay &&
                    year == selectedYear &&
                    month == selectedMonth)

            if (isToday) {
                dayView.setBackgroundResource(R.drawable.bg_circle_gradient_blue)
                dayView.setTextColor(Color.WHITE)
            } else if (isSelected) {
                dayView.setBackgroundResource(R.drawable.bg_circle_selected)
                dayView.setTextColor(Color.WHITE)
            }

            val dayOfWeek = PersianDate().apply {
                setShYear(year)
                setShMonth(month)
                setShDay(day)
            }.dayOfWeek()

            if (dayOfWeek == 6 && !isToday) {
                dayView.setTextColor(requireContext().getColor(R.color.red))
            }

            dayView.setOnClickListener {
                onDayClick(year, month, day)
            }

            gridDays.addView(dayView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onDayClick(year: Int, month: Int, day: Int) {
        selectedYear = year
        selectedMonth = month
        selectedDay = day

        setupCalendar(year, month)
        setupEvents(year, month, day)
        updateAllDates(year, month, day)


    }

    private fun setupEvents(year: Int, month: Int, day: Int) {
        if (!::txtEvent.isInitialized) return

        val key = "$month-$day"
        var event: String? = null

        if (holidaysMap.containsKey(key)) {
            event = holidaysMap[key]
        }

        if (event == null || event.isEmpty() || event == "null") {
            when {
                year == currentYear && month == currentMonth && day == currentDay -> {
                    event = getString(R.string.event_today)
                }
                else -> {
                    val dayOfWeek = PersianDate().apply {
                        setShYear(year)
                        setShMonth(month)
                        setShDay(day)
                    }.dayOfWeek()

                    event = if (dayOfWeek == 6) {
                        getString(R.string.event_friday)
                    } else {
                        getString(R.string.event_normal_day)
                    }
                }
            }
        }

        txtEvent.text = event ?: getString(R.string.event_normal_day)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        gridDays.post {
            if (gridDays.width > 0 && gridDays.childCount == 0) {
                setupCalendar(currentYear, currentMonth)
            }
        }
    }
}