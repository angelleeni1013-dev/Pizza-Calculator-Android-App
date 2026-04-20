package com.example.a0209295_dmt22113n_a1

import android.graphics.Color
import android.graphics.Paint
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.example.a0209295_dmt22113n_a1.databinding.ActivityMainBinding
import com.google.android.material.card.MaterialCardView
import kotlinx.parcelize.Parcelize
import java.util.Locale
import kotlin.math.ceil


// Data class to hold all detailed calculation data for history, now Parcelable
@Parcelize
data class CalculationResult(
    val id: Long = System.currentTimeMillis(),
    val adults: Int,
    val children: Int,
    val hungerLevelName: String,
    val slicesPerPizza: Int,
    val selectedPizzaTypes: List<String>,
    val slicesNeeded: Int,
    val pizzasNeeded: Int,
    val totalCalories: Int,
    val timestamp: String = java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(java.util.Date())
) : Parcelable {
    val pizzaList: String
        get() = selectedPizzaTypes.joinToString(", ")

    val peopleInfo: String
        get() = "$adults adults, $children children"
}

class MainActivity : AppCompatActivity() {

    // --- GLOBAL VARIABLES (State Management) ---
    private lateinit var binding: ActivityMainBinding

    // Stepper State
    private var adultsCount = 0
    private var childrenCount = 0
    private val maxCount = 100

    // Card Selection State
    private val selectedPizzas = mutableSetOf<String>()
    // Using lazy for colors to ensure context is available
    private val selectedBorderColor by lazy { ContextCompat.getColor(this, R.color.selected_border_color) }
    private val defaultBorderColor = Color.LTGRAY
    // Defining selected background color here. (Should be defined in colors.xml)
    private val selectedCardBgColor by lazy { "#E3F2FD".toColorInt() }

    // History State
    private val calculationHistory = ArrayList<CalculationResult>()

    // Music Player State
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var playbackPosition: Int = 0 // Stores the position when paused/stopped

    // Pizza Calorie Map (Example values)
    private val pizzaCalories = mapOf(
        "Pepperoni" to 2200,
        "Durian" to 2800,
        "Margherita" to 1800
    )

    // Keys for persistence (screen rotation)
    companion object {
        private const val STATE_ADULTS = "state_adults"
        private const val STATE_CHILDREN = "state_children"
        private const val STATE_PIZZAS = "state_pizzas"
        private const val STATE_HISTORY = "state_history"
        private const val STATE_HISTORY_VISIBLE = "state_history_visible"
        private const val STATE_PLAYBACK_POSITION = "state_playback_position"
        private const val STATE_IS_PLAYING = "state_is_playing"

        // Keys for the history toggle text
        private const val VIEW_HISTORY_TEXT = "View History"
        private const val HIDE_HISTORY_TEXT = "Hide History"

        // Validation Range for Slices
        private const val MIN_SLICES = 4
        private const val MAX_SLICES = 16
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. View Binding Setup (MUST be first)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            // Underline the history link immediately on creation
            textViewHistoryToggle.paintFlags = textViewHistoryToggle.paintFlags or Paint.UNDERLINE_TEXT_FLAG

            // 2. Restore state if screen was rotated
            if (savedInstanceState != null) {
                restoreState(savedInstanceState)
            }

            // 3. Component Initialization
            setupSteppers()
            setupCardListeners()
            setupMusicPlayer()
            setupCalculateButton()
            setupHistoryButton()

            // 4. Initial UI Update (called after restoreState)
            updateStepperUI()
            updateCardUI()
            updateHistoryDisplay()

            // Set initial results text (Using the IDs that are likely present)
            if (textCurrentResultsDisplay.text.isNullOrEmpty() || textCurrentResultsDisplay.text == "Awaiting calculation…") {
                textCurrentResultsDisplay.text = getString(R.string.results)
            }
        }
    }

    // --- LIFECYCLE FOR MUSIC AND STATE MANAGEMENT ---

    /**
     * Called when the Activity is moving into the background (rotation or Home press).
     * Saves the current playback position and pauses the music.
     */
    override fun onPause() {
        super.onPause()
        mediaPlayer?.let {
            if (it.isPlaying) {
                playbackPosition = it.currentPosition // Save current position
                it.pause() // Pause music
                isPlaying = true // Save state that it was playing
            } else {
                isPlaying = false
            }
        }
    }

    /**
     * Called when the Activity is brought back to the foreground (after rotation or app launch).
     * Restores the playback position. Music does NOT automatically start here.
     */
    override fun onResume() {
        super.onResume()
        // Seek to the saved position. Music starts only via Calculate/Music Button.
        mediaPlayer?.seekTo(playbackPosition)
    }

    /**
     * Releases media resources completely.
     */
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // -------------------------------------------------------------------------
    // PERSISTENCE (STATE MANAGEMENT)
    // -------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun restoreState(savedInstanceState: Bundle) {
        adultsCount = savedInstanceState.getInt(STATE_ADULTS, 0)
        childrenCount = savedInstanceState.getInt(STATE_CHILDREN, 0)
        playbackPosition = savedInstanceState.getInt(STATE_PLAYBACK_POSITION, 0) // Restore position
        isPlaying = savedInstanceState.getBoolean(STATE_IS_PLAYING, false) // Restore status

        val pizzaArray = savedInstanceState.getStringArray(STATE_PIZZAS)
        if (pizzaArray != null) {
            selectedPizzas.addAll(pizzaArray)
        }

        // Restore history data
        val restoredHistory = savedInstanceState.getParcelableArrayList<CalculationResult>(STATE_HISTORY)
        if (restoredHistory != null) {
            calculationHistory.addAll(restoredHistory)
        }

        // Restore history visibility state
        val historyWasVisible = savedInstanceState.getBoolean(STATE_HISTORY_VISIBLE, false)

        if (calculationHistory.isNotEmpty()) {
            // If we have history, show the latest result before restoring the visibility choice
            showInlineResults(calculationHistory.first(), initialLoad = true)

            if (historyWasVisible) {
                // If history was visible before rotation, show it now
                binding.layoutResultsHistory.visibility = View.VISIBLE
                binding.scrollCurrentResultDisplay.visibility = View.GONE
                setHistoryLinkText(true)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ADULTS, adultsCount)
        outState.putInt(STATE_CHILDREN, childrenCount)
        outState.putStringArray(STATE_PIZZAS, selectedPizzas.toTypedArray())

        // Save media state
        outState.putInt(STATE_PLAYBACK_POSITION, playbackPosition)
        outState.putBoolean(STATE_IS_PLAYING, isPlaying)

        // Save history
        outState.putParcelableArrayList(STATE_HISTORY, calculationHistory)

        // Save visibility state of the history box
        outState.putBoolean(STATE_HISTORY_VISIBLE, binding.layoutResultsHistory.isVisible)
    }

    // -------------------------------------------------------------------------
    // STEPPER LOGIC
    // -------------------------------------------------------------------------

    // Helper function to update count safely
    private fun updateCount(current: Int, increment: Boolean): Int {
        return when {
            increment && current < maxCount -> current + 1
            !increment && current > 0 -> current - 1
            else -> current
        }
    }

    private fun setupSteppers() {
        setupAdultsListeners()
        setupChildrenListeners()
    }

    private fun updateStepperUI() {
        with(binding) {
            textAdultsCount.text = adultsCount.toString()
            buttonAdultsDecrement.isEnabled = adultsCount > 0
            buttonAdultsIncrement.isEnabled = adultsCount < maxCount

            textChildrenCount.text = childrenCount.toString()
            buttonChildrenDecrement.isEnabled = childrenCount > 0
            buttonChildrenIncrement.isEnabled = childrenCount < maxCount
        }
    }

    private fun setupAdultsListeners() {
        binding.buttonAdultsIncrement.setOnClickListener {
            adultsCount = updateCount(adultsCount, true)
            updateStepperUI()
        }
        binding.buttonAdultsDecrement.setOnClickListener {
            adultsCount = updateCount(adultsCount, false)
            updateStepperUI()
        }
    }

    private fun setupChildrenListeners() {
        binding.buttonChildrenIncrement.setOnClickListener {
            childrenCount = updateCount(childrenCount, true)
            updateStepperUI()
        }
        binding.buttonChildrenDecrement.setOnClickListener {
            childrenCount = updateCount(childrenCount, false)
            updateStepperUI()
        }
    }

    // -------------------------------------------------------------------------
    // CARD SELECTION LOGIC
    // -------------------------------------------------------------------------

    private fun updateCardUI() {
        with(binding) {
            val pizzaMap = mapOf(
                cardPepperoni to getString(R.string.pepperoni),
                cardDurian to getString(R.string.durian),
                cardMargherita to getString(R.string.margherita)
            )
            pizzaMap.forEach { (card, type) ->
                toggleCardVisuals(card, selectedPizzas.contains(type))
            }
        }
    }

    private fun setupCardListeners() {
        binding.cardPepperoni.setOnClickListener {
            toggleCardSelection(binding.cardPepperoni, getString(R.string.pepperoni))
        }
        binding.cardDurian.setOnClickListener {
            toggleCardSelection(binding.cardDurian, getString(R.string.durian))
        }
        binding.cardMargherita.setOnClickListener {
            toggleCardSelection(binding.cardMargherita, getString(R.string.margherita))
        }
    }

    private fun toggleCardSelection(card: MaterialCardView, pizzaType: String) {
        if (selectedPizzas.contains(pizzaType)) {
            selectedPizzas.remove(pizzaType)
            toggleCardVisuals(card, false)
        } else {
            selectedPizzas.add(pizzaType)
            toggleCardVisuals(card, true)
        }
        Log.d("PizzaApp", "Selected: $selectedPizzas")
    }

    private fun toggleCardVisuals(card: MaterialCardView, isSelected: Boolean) {
        card.apply {
            if (isSelected) {
                setCardBackgroundColor(selectedCardBgColor)
                cardElevation = 8f
                strokeWidth = 5
                strokeColor = selectedBorderColor
            } else {
                setCardBackgroundColor(Color.WHITE)
                cardElevation = 4f
                strokeWidth = 0
                strokeColor = defaultBorderColor
            }
        }
    }

    // -------------------------------------------------------------------------
    // HISTORY TOGGLE LOGIC
    // -------------------------------------------------------------------------

    private fun setHistoryLinkText(isHistoryVisible: Boolean) {
        val text = if (isHistoryVisible) HIDE_HISTORY_TEXT else VIEW_HISTORY_TEXT
        val spannableContent = SpannableString(text)
        spannableContent.setSpan(UnderlineSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.textViewHistoryToggle.text = spannableContent
    }

    private fun setupHistoryButton() {
        // Set initial text state (assuming history starts GONE, current result visible)
        setHistoryLinkText(false)

        binding.textViewHistoryToggle.setOnClickListener {
            val historyIsVisible = binding.layoutResultsHistory.isVisible

            if (historyIsVisible) {
                // HIDE HISTORY, SHOW CURRENT RESULT
                binding.layoutResultsHistory.visibility = View.GONE
                binding.scrollCurrentResultDisplay.visibility = View.VISIBLE
                setHistoryLinkText(false) // Set text to "View History"
            } else {
                // SHOW HISTORY, HIDE CURRENT RESULT
                binding.layoutResultsHistory.visibility = View.VISIBLE
                binding.scrollCurrentResultDisplay.visibility = View.GONE
                setHistoryLinkText(true) // Set text to "Hide History"

                // Scroll to the top of the history list when opening
                binding.scrollHistoryDisplay.post {
                    binding.scrollHistoryDisplay.fullScroll(View.FOCUS_UP)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // CALCULATE & RESET LOGIC
    // -------------------------------------------------------------------------

    private fun setupCalculateButton() {
        binding.buttonCalculate.setOnClickListener { calculatePizzaNeeds() }
        binding.buttonReset.setOnClickListener { resetAllInputs() }
    }

    private fun calculatePizzaNeeds() {
        val slicesPerPizza = binding.editNumSlices.text.toString().toIntOrNull()

        // Determine hunger level & slices per person
        val checkedId = binding.groupHunger.checkedRadioButtonId
        val (adultSlicesPerPerson, hungerLevelName) = when (checkedId) {
            R.id.radio_light -> Pair(1, getString(R.string.light))
            R.id.radio_medium -> Pair(2, getString(R.string.medium))
            R.id.radio_ravenous -> Pair(4, getString(R.string.ravenous))
            else -> Pair(0, "")
        }

        // --- Validation 1: Required Fields and Slices Range ---
        if (slicesPerPizza == null || slicesPerPizza < MIN_SLICES || slicesPerPizza > MAX_SLICES || adultsCount + childrenCount == 0 || selectedPizzas.isEmpty()) {
            val errorMessage = when {
                slicesPerPizza == null || slicesPerPizza < MIN_SLICES || slicesPerPizza > MAX_SLICES ->
                    "Please enter the number of slices between $MIN_SLICES and $MAX_SLICES."
                else -> getString(R.string.error_empty_field)
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            return
        }

        // --- CORE CALCULATION ---
        val childSlicesNeeded = childrenCount * (adultSlicesPerPerson * 0.5)
        val totalSlicesNeededDecimal = (adultsCount * adultSlicesPerPerson) + childSlicesNeeded
        val finalTotalSlicesNeeded = ceil(totalSlicesNeededDecimal).toInt()

        val numPizzasNeededDecimal = finalTotalSlicesNeeded.toDouble() / slicesPerPizza.toDouble()
        val finalNumPizzas = ceil(numPizzasNeededDecimal).toInt()

        // --- Validation 2: Pizza Types vs. Pizzas Needed ---
        if (selectedPizzas.size > finalNumPizzas) {
            Toast.makeText(this, "The number of pizza types selected (${selectedPizzas.size}) cannot exceed the total pizzas needed (${finalNumPizzas}). Please adjust your selection or the input.", Toast.LENGTH_LONG).show()
            return // Stop the calculation and provide feedback
        }

        // --- CALORIES ---
        val totalSelectedCalories = selectedPizzas.sumOf { pizzaCalories[it] ?: 0 }
        val averageCaloriesPerPizza = if (selectedPizzas.isNotEmpty()) totalSelectedCalories / selectedPizzas.size else 2000
        val finalTotalCalories = finalNumPizzas * averageCaloriesPerPizza

        // --- RESULT OBJECT ---
        val result = CalculationResult(
            adults = adultsCount,
            children = childrenCount,
            hungerLevelName = hungerLevelName,
            slicesPerPizza = slicesPerPizza,
            selectedPizzaTypes = selectedPizzas.toList(),
            pizzasNeeded = finalNumPizzas,
            slicesNeeded = finalTotalSlicesNeeded,
            totalCalories = finalTotalCalories
        )

        // --- HISTORY UPDATE ---
        calculationHistory.add(0, result)

        // --- DISPLAY RESULT & HIDE HISTORY ---
        showInlineResults(result)

        // --- Toast Feedback ---
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
    }


    private fun resetAllInputs() {
        // Stepper Reset
        adultsCount = 0
        childrenCount = 0
        updateStepperUI()

        with(binding) {
            // Input/Radio Reset
            editNumSlices.setText("")
            radioLight.isChecked = true

            // Card Selection Reset
            val allCards = listOf(cardPepperoni, cardDurian, cardMargherita)
            for (card in allCards) {
                toggleCardVisuals(card, false)
            }
            selectedPizzas.clear()

            // UI Display Reset
            textCurrentResultsDisplay.text = getString(R.string.results)

            // Ensure Current is visible and History is hidden
            scrollCurrentResultDisplay.visibility = View.VISIBLE
            layoutResultsHistory.visibility = View.GONE
            setHistoryLinkText(false) // Reset link text to "View History"
        }

        // Music Reset
        mediaPlayer?.let {
            it.pause()
            it.seekTo(0)
            isPlaying = false
            playbackPosition = 0 // Reset position
        }

        Toast.makeText(this, R.string.toast_reset, Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------------------
    // INLINE RESULTS & HISTORY DISPLAY
    // -------------------------------------------------------------------------

    // This function formats all history entries using resource strings and placeholders.
    private fun formatHistoryText(): String {
        val sb = StringBuilder()
        if (calculationHistory.isEmpty()) {
            return getString(R.string.no_history)
        }

        // Loop through the history list and format each entry
        calculationHistory.forEachIndexed { index, item ->
            // Use getString with placeholders for proper localization/readability
            sb.append(getString(R.string.history_entry_format,
                item.timestamp,
                if (index == 0) "(LATEST)" else "",
                item.pizzaList,
                item.peopleInfo,
                item.hungerLevelName,
                item.slicesPerPizza,
                item.slicesNeeded,
                item.pizzasNeeded,
                item.totalCalories,
                "-".repeat(40)
            ))
        }
        return sb.toString()
    }

    private fun updateHistoryDisplay() {
        binding.textHistoryDisplay.text = formatHistoryText()
    }

    /**
     * Updates the UI with the calculation result and handles media start/resume.
     * @param initialLoad True if called during state restoration (to prevent media starting).
     */
    private fun showInlineResults(result: CalculationResult, initialLoad: Boolean = false) {

        // 1. Update Current Results Display
        binding.textCurrentResultsDisplay.text =
            getString(R.string.current_result_summary,
                result.pizzaList,
                result.peopleInfo,
                result.hungerLevelName,
                result.slicesPerPizza,
                result.slicesNeeded,
                result.pizzasNeeded,
                result.totalCalories)

        // 2. Start/Resume music playback (only if the user had it playing before rotation)
        if (!initialLoad && mediaPlayer != null) {
            if (isPlaying && mediaPlayer?.isPlaying == false) {
                // Resume playback from saved position (which was set in onResume via seekTo)
                mediaPlayer?.start()
                Toast.makeText(this, R.string.music_start, Toast.LENGTH_SHORT).show()
            } else if (mediaPlayer?.isPlaying == false) {
                // Start from beginning if not playing and not resuming
                mediaPlayer?.start()
                isPlaying = true
                Toast.makeText(this, R.string.music_start, Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Update History Display
        updateHistoryDisplay()

        // 4. Ensure current result is visible and history is hidden
        binding.scrollCurrentResultDisplay.visibility = View.VISIBLE
        binding.layoutResultsHistory.visibility = View.GONE
        setHistoryLinkText(false) // Reset link text to "View History"
    }

    // -------------------------------------------------------------------------
    // MUSIC PLAYER SETUP
    // -------------------------------------------------------------------------

    private fun setupMusicPlayer() {
        // NOTE: R.raw.pizza_song assumes you have a file named 'pizza_song.mp3' or similar in res/raw/
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.pizza_song)
            mediaPlayer?.isLooping = true
        } catch (e: Exception) {
            Log.e("PizzaApp", "Error setting up media player: ${e.message}")
        }

        binding.buttonMusicPlayer.setOnClickListener {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    isPlaying = false // Set internal state
                    Toast.makeText(this, R.string.music_pause, Toast.LENGTH_SHORT).show()
                } else {
                    mediaPlayer?.start()
                    isPlaying = true // Set internal state
                    Toast.makeText(this, R.string.music_start, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}