package com.easyfitness.programs

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.View.GONE
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.easyfitness.*
import com.easyfitness.DAO.*
import com.easyfitness.DAO.DAOMachine.*
import com.easyfitness.machines.ExerciseDetailsPager
import com.easyfitness.machines.MachineCursorAdapter
import com.easyfitness.utils.BtnOnPostiomClickListener
import com.easyfitness.utils.DateConverter
import com.easyfitness.utils.ImageUtil
import com.easyfitness.utils.UnitConverter
import com.ikovac.timepickerwithseconds.MyTimePickerDialog
import com.ikovac.timepickerwithseconds.TimePicker
import com.onurkaganaldemir.ktoastlib.KToast
import kotlinx.android.synthetic.main.tab_program_with_exercises.*
import timber.log.Timber
import java.util.*

class ExercisesInProgramFragment : Fragment(R.layout.tab_program_with_exercises) {
    private lateinit var mainActivity: MainActivity
    private var lTableColor = 1
    private var machineListDialog: AlertDialog? = null
    private var selectedType = TYPE_FONTE
    private lateinit var daoProgram: DAOProgram
    private var programId: Long = 1
    var programs: MutableList<String>? = null
    private var exercisesList: MutableList<ExerciseInProgram> = ArrayList<ExerciseInProgram>().toMutableList()
    private lateinit var linearLayoutManager: LinearLayoutManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        daoProgram = DAOProgram(context)
        programs = daoProgram.allProgramsNames
        daoExerciseInProgram = DAOExerciseInProgram(requireContext())
        if (programs == null || programs!!.isEmpty()) {
            val profileId: Long = (requireActivity() as MainActivity).currentProfile.id
            val programsFragment = ProgramsFragment.newInstance("", profileId)
            Toast.makeText(context, R.string.add_program_first, Toast.LENGTH_LONG).show()
            requireActivity().supportFragmentManager.commit {
                addToBackStack(null)
                add(R.id.fragment_container, programsFragment)
            }
        } else {
            programId = daoProgram.getRecord(programs!![0])!!.id
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, programs!!)
            programSelect.adapter = adapter
            exercisesList = daoExerciseInProgram.getAllExerciseInProgram(programId).toMutableList()
            exercisesRecycler.adapter = ExerciseInProgramAdapter(requireContext(), exercisesList, itemClickDeleteRecord, null)
            exercisesRecycler.layoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)

            programSelect.onItemSelectedListener = object :
                AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>,
                                            view: View?, position: Int, id: Long) {
                    programId = daoProgram.getRecord(programs!![position])!!.id
                    refreshData()
                    Toast.makeText(context, getString(R.string.program_selection) + " " + programs!![position], Toast.LENGTH_SHORT).show()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
        detailsLayout.visibility = View.VISIBLE
        addButton.setOnClickListener(clickAddButton)
        machineListButton.setOnClickListener(onClickMachineListWithIcons) //onClickMachineList
        seriesEdit.onFocusChangeListener = touchRazEdit
        repetitionEdit.onFocusChangeListener = touchRazEdit
        poidsEdit.onFocusChangeListener = touchRazEdit
        distanceEdit.onFocusChangeListener = touchRazEdit
        durationEdit.setOnClickListener(clickDateEdit)
        secondsEdit.onFocusChangeListener = touchRazEdit
        exerciseEdit.setOnKeyListener(checkExerciseExists)
        exerciseEdit.onFocusChangeListener = touchRazEdit
        exerciseEdit.onItemClickListener = onItemClickFilterList
        restTimeEdit.onFocusChangeListener = restTimeEditChange
        restTimeCheck.setOnCheckedChangeListener(restTimeCheckChange)
        bodybuildingSelector.setOnClickListener(clickExerciseTypeSelector)
        cardioSelector.setOnClickListener(clickExerciseTypeSelector)
        staticExerciseSelector.setOnClickListener(clickExerciseTypeSelector)
        restoreSharedParams()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        var weightUnit = UnitConverter.UNIT_KG
        try {
            weightUnit = sharedPreferences.getString(SettingsFragment.WEIGHT_UNIT_PARAM, "0")?.toInt()!!
        } catch (e: NumberFormatException) {
            Timber.d("Conversion Not important")
        }
        unitSpinner.setSelection(weightUnit)
        val distanceUnit: Int
        distanceUnit = try {
            sharedPreferences.getString(SettingsFragment.DISTANCE_UNIT_PARAM, "0")?.toInt()!!
        } catch (e: NumberFormatException) {
            UnitConverter.UNIT_KM
        }
        unitDistanceSpinner.setSelection(distanceUnit)
        // Initialization of the database
        mDbMachine = DAOMachine(context)
        selectedType = TYPE_FONTE
        exerciseImage.setOnClickListener {
            val m = mDbMachine.getMachine(exerciseEdit.text.toString())
            if (m != null) {
                val profileId: Long = (requireActivity() as MainActivity).currentProfile.id
                val machineDetailsFragment = ExerciseDetailsPager.newInstance(m.id, profileId)
                requireActivity().supportFragmentManager.commit {
                    addToBackStack(null)
                    add(R.id.fragment_container, machineDetailsFragment)
                }
            }
        }

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, dragged: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = dragged.adapterPosition
                val toPosition = target.adapterPosition
                val listOfExercises: List<ExerciseInProgram> = exercisesList
                if (fromPosition < toPosition) {
                    for (i in fromPosition until toPosition) {
                        Collections.swap(listOfExercises, i, i + 1)
                        val order1: Long = listOfExercises[i].order
                        val order2: Long = listOfExercises[i + 1].order
                        listOfExercises[i].order = order2
                        listOfExercises[i + 1].order = order1
                        daoExerciseInProgram.updateString(listOfExercises[i], DAOExerciseInProgram.ORDER_EXECUTION, order2.toString())
                        daoExerciseInProgram.updateString(listOfExercises[i + 1], DAOExerciseInProgram.ORDER_EXECUTION, order1.toString())
                    }
                } else {
                    for (i in fromPosition downTo toPosition + 1) {
                        Collections.swap(listOfExercises, i, i - 1)
                        val order1: Long = listOfExercises[i].order
                        val order2: Long = listOfExercises[i - 1].order
                        listOfExercises[i].order = order2
                        listOfExercises[i - 1].order = order1
                        daoExerciseInProgram.updateString(listOfExercises[i], DAOExerciseInProgram.ORDER_EXECUTION, order2.toString())
                        daoExerciseInProgram.updateString(listOfExercises[i - 1], DAOExerciseInProgram.ORDER_EXECUTION, order1.toString())
                    }
                }
                exercisesRecycler.adapter!!.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(exercisesRecycler)
        linearLayoutManager = LinearLayoutManager(requireContext())
        exercisesRecycler.layoutManager = linearLayoutManager
    }

    private val durationSet = MyTimePickerDialog.OnTimeSetListener { _: TimePicker?, hourOfDay: Int, minute: Int, second: Int ->
        val strMinute: String = if (minute < 10) "0$minute" else minute.toString()
        val strHour: String = if (hourOfDay < 10) "0$hourOfDay" else hourOfDay.toString()
        val strSecond: String = if (second < 10) "0$second" else second.toString()
        val date = "$strHour:$strMinute:$strSecond"
        durationEdit.text = date
        hideKeyboard()
    }
    private lateinit var daoExerciseInProgram: DAOExerciseInProgram
    private lateinit var mDbMachine: DAOMachine
    private val clickExerciseTypeSelector = View.OnClickListener { v: View ->
        when (v.id) {
            R.id.staticExerciseSelector -> changeExerciseTypeUI(TYPE_STATIC, true)
            R.id.cardioSelector -> changeExerciseTypeUI(TYPE_CARDIO, true)
            R.id.bodybuildingSelector -> changeExerciseTypeUI(TYPE_FONTE, true)
            else -> changeExerciseTypeUI(TYPE_FONTE, true)
        }
    }
    private val checkExerciseExists = View.OnKeyListener { _: View?, _: Int, _: KeyEvent? ->
        val lMach = mDbMachine.getMachine(exerciseEdit.text.toString())
        if (lMach == null) {
            showExerciseTypeSelector(true)
        } else {
            changeExerciseTypeUI(lMach.type, false)
        }
        false
    }
    private val restTimeEditChange = OnFocusChangeListener { _: View?, hasFocus: Boolean ->
        if (!hasFocus) {
            saveSharedParams()
        }
    }
    private val restTimeCheckChange = CompoundButton.OnCheckedChangeListener { _: CompoundButton?, _: Boolean -> saveSharedParams() }
    private val itemClickDeleteRecord = BtnOnPostiomClickListener { idToDelete: Long, positionOnList: Int -> showDeleteDialog(idToDelete, positionOnList) }

    @SuppressLint("SetTextI18n")
    private val clickAddButton = View.OnClickListener {
        if (exerciseEdit.text.toString().isEmpty()) {
            KToast.warningToast(requireActivity(), resources.getText(R.string.missinginfo).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT)
            return@OnClickListener
        }
        val exerciseType: Int = selectedType
        var restTime = 60
        try {
            restTime = restTimeEdit.text.toString().toInt()
        } catch (e: NumberFormatException) {
            restTimeEdit.setText("60")
        }
        val currentTimeAsOrder: Long = System.currentTimeMillis()
        when (exerciseType) {
            TYPE_FONTE -> {
                if (seriesEdit.text.toString().isEmpty() ||
                    repetitionEdit.text.toString().isEmpty() ||
                    poidsEdit.text.toString().isEmpty()) {
                    KToast.warningToast(requireActivity(), resources.getText(R.string.missinginfo).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT)
                    return@OnClickListener
                }
                var tmpPoids = poidsEdit.text.toString().replace(",".toRegex(), ".").toFloat()  /* Weight conversion */
                var unitPoids = UnitConverter.UNIT_KG // Kg
                val mContext = requireContext()
                if (unitSpinner.selectedItem.toString() == mContext.getString(R.string.LbsUnitLabel)) {
                    tmpPoids = UnitConverter.LbstoKg(tmpPoids) // Always convert to KG
                    unitPoids = UnitConverter.UNIT_LBS // LBS
                }
                daoExerciseInProgram.addRecord(
                    currentTimeAsOrder,
                    programId, restTime,
                    exerciseEdit.text.toString(),
                    TYPE_FONTE, seriesEdit.text.toString().toInt(), repetitionEdit.text.toString().toInt(),
                    tmpPoids,  // Always save in KG
                    profil, unitPoids,  // Store Unit for future display
                    "",  //Notes,
                    "", 0f, 0, 0, 0
                )
                if (mDbMachine.getMachine(exerciseEdit.text.toString()) == null)
                    mDbMachine.addMachine(exerciseEdit.text.toString(), "", TYPE_FONTE, "", false, null)
                exercisesList = daoExerciseInProgram.getAllExerciseInProgram(programId)
                val exercise = exercisesList[exercisesList.size - 1]
                (exercisesRecycler.adapter!! as ExerciseInProgramAdapter).add(exercise)
            }
            TYPE_STATIC -> {
                if (seriesEdit.text.toString().isEmpty() ||
                    secondsEdit.text.toString().isEmpty() ||
                    poidsEdit.text.toString().isEmpty()) {
                    KToast.warningToast(requireActivity(), resources.getText(R.string.missinginfo).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT)
                    return@OnClickListener
                }
                /* Weight conversion */
                var tmpPoids = poidsEdit.text.toString().replace(",".toRegex(), ".").toFloat()
                var unitPoids = UnitConverter.UNIT_KG // Kg
                if (unitSpinner.selectedItem.toString() == requireContext().getString(R.string.LbsUnitLabel)) {
                    tmpPoids = UnitConverter.LbstoKg(tmpPoids) // Always convert to KG
                    unitPoids = UnitConverter.UNIT_LBS // LBS
                }
                try {
                    restTime = restTimeEdit.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    restTime = 0
                    restTimeEdit.setText("0")
                }
                daoExerciseInProgram.addRecord(
                    currentTimeAsOrder,
                    programId,
                    restTime,
                    exerciseEdit.text.toString(), TYPE_STATIC, seriesEdit.text.toString().toInt(),
                    1, tmpPoids, profil, unitPoids,  // Store Unit for future display
                    "", "", 0F, 0, secondsEdit.text.toString().toInt(), 0
                )
                if (mDbMachine.getMachine(exerciseEdit.text.toString()) == null)
                    mDbMachine.addMachine(exerciseEdit.text.toString(), "", TYPE_STATIC, "", false, null)
                exercisesList = daoExerciseInProgram.getAllExerciseInProgram(programId)
                val exercise = exercisesList[exercisesList.size - 1]
                (exercisesRecycler.adapter!! as ExerciseInProgramAdapter).add(exercise)
            }
            TYPE_CARDIO -> {
                if (durationEdit.text.toString().isEmpty() &&  // Only one is mandatory
                    distanceEdit.text.toString().isEmpty()) {
                    KToast.warningToast(requireActivity(),
                        resources.getText(R.string.missinginfo).toString() + " Distance missing",
                        Gravity.BOTTOM, KToast.LENGTH_SHORT)
                    return@OnClickListener
                }
                var duration = 0L
                try {
                    if (durationEdit.text.toString().isNotEmpty()) {
                        duration = DateConverter.durationStringToLong(durationEdit.text.toString())
                    }
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                    duration = 0
                }
                var distance: Float
                distance = if (distanceEdit.text.toString().isEmpty()) {
                    0f
                } else {
                    distanceEdit.text.toString().replace(",".toRegex(), ".").toFloat()
                }
                var unitDistance = UnitConverter.UNIT_KM
                if (unitDistanceSpinner.selectedItem.toString()
                    == requireContext().getString(R.string.MilesUnitLabel)) {
                    distance = UnitConverter.MilesToKm(distance) // Always convert to KG
                    unitDistance = UnitConverter.UNIT_MILES
                }
                daoExerciseInProgram.addRecord(
                    currentTimeAsOrder,
                    programId, restTime,
                    exerciseEdit.text.toString(),
                    TYPE_CARDIO,
                    1,
                    1, 0f,
                    profil,
                    1,
                    "",
                    "",
                    distance,
                    duration,
                    0,
                    unitDistance)
                if (mDbMachine.getMachine(exerciseEdit.text.toString()) == null)
                    mDbMachine.addMachine(exerciseEdit.text.toString(), "", TYPE_CARDIO, "", false, null)
                exercisesList = daoExerciseInProgram.getAllExerciseInProgram(programId)
                val exercise = exercisesList[exercisesList.size - 1]
                (exercisesRecycler.adapter!! as ExerciseInProgramAdapter).add(exercise)
            }
        }
        requireActivity().findViewById<View>(R.id.drawer_layout)?.requestFocus()
        hideKeyboard()
        lTableColor = (lTableColor + 1) % 2 // Change the color each time you add data
        refreshData()
        /* Reinitialisation des machines */
        val adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_dropdown_item_1line, daoExerciseInProgram.getAllExerciseInProgramAsList(programId))
        exerciseEdit.setAdapter(adapter)
        addButton.setText(R.string.AddLabel)
    }
    private val onClickMachineListWithIcons = View.OnClickListener { v ->
        val oldCursor: Cursor
        if (machineListDialog != null && machineListDialog!!.isShowing) {        // In case the dialog is already open
            return@OnClickListener
        }
        val machineList = ListView(v.context)
        val c: Cursor? = mDbMachine.allMachines
        if (c == null || c.count == 0) {
            KToast.warningToast(requireActivity(), resources.getText(R.string.createExerciseFirst).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT)
            machineList.adapter = null
        } else {
            if (machineList.adapter == null) {
                val mTableAdapter = MachineCursorAdapter(activity, c, 0, mDbMachine)
                machineList.adapter = mTableAdapter
            } else {
                val mTableAdapter = machineList.adapter as MachineCursorAdapter
                oldCursor = mTableAdapter.swapCursor(c)
                oldCursor?.close()
            }
            machineList.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
                val textView = view.findViewById<TextView>(R.id.LIST_MACHINE_ID)
                val machineID = textView.text.toString().toLong()
                val lMachineDb = DAOMachine(context)
                val lMachine = lMachineDb.getMachine(machineID)
                setCurrentExercise(lMachine.name)
                mainActivity.findViewById<View>(R.id.drawer_layout).requestFocus()
                hideKeyboard()
                if (machineListDialog!!.isShowing) {
                    machineListDialog!!.dismiss()
                }
            }
            val builder = AlertDialog.Builder(v.context)
            builder.setTitle(R.string.selectMachineDialogLabel)
            builder.setView(machineList)
            machineListDialog = builder.create()
            machineListDialog!!.show()
        }
    }
    private val onItemClickFilterList = OnItemClickListener { _: AdapterView<*>?, _: View?, _: Int, _: Long -> setCurrentExercise(exerciseEdit.text.toString()) }

    //Required for cardio/duration
    private val clickDateEdit = View.OnClickListener { v: View ->
        when (v.id) {
            R.id.durationEdit -> showTimePicker(durationEdit)
        }
    }
    private val touchRazEdit = OnFocusChangeListener { v: View, hasFocus: Boolean ->
        if (hasFocus) {
            when (v.id) {
                R.id.seriesEdit -> seriesEdit.setText("")
                R.id.repetitionEdit -> repetitionEdit.setText("")
                R.id.secondsEdit -> secondsEdit.setText("")
                R.id.poidsEdit -> poidsEdit.setText("")
                R.id.durationEdit -> showTimePicker(durationEdit)
                R.id.distanceEdit -> distanceEdit.setText("")
                R.id.exerciseEdit -> {
                    exerciseImage.setImageResource(R.drawable.ic_gym_bench_50dp)
                    minMaxLayout.visibility = GONE
                    showExerciseTypeSelector(true)
                }
            }
            v.post {
                val imm = Objects.requireNonNull(requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE)) as InputMethodManager
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            if (v.id == R.id.exerciseEdit) { // If a creation of a new machine is not ongoing.
                if (exerciseTypeSelectorLayout.visibility == GONE) setCurrentExercise(exerciseEdit.text.toString())
            }
        }
    }

    private fun showDeleteDialog(idToDelete: Long, position: Int) {
        SweetAlertDialog(requireContext(), SweetAlertDialog.WARNING_TYPE)
            .setTitleText(getString(R.string.DeleteRecordDialog))
            .setContentText(resources.getText(R.string.areyousure).toString())
            .setCancelText(resources.getText(R.string.global_no).toString())
            .setConfirmText(resources.getText(R.string.global_yes).toString())
            .showCancelButton(true)
            .setConfirmClickListener { sDialog: SweetAlertDialog ->
                daoExerciseInProgram.deleteRecord(idToDelete)
                (exercisesRecycler.adapter!! as ExerciseInProgramAdapter).removeAt(position)
                exercisesList = daoExerciseInProgram.getAllExerciseInProgram(programId).toMutableList()
                KToast.infoToast(requireActivity(), resources.getText(R.string.removedid).toString(), Gravity.BOTTOM, KToast.LENGTH_LONG)
                sDialog.dismissWithAnimation()
            }
            .show()
    }

    override fun onStart() {
        super.onStart()
        mainActivity = this.activity as MainActivity
        refreshData()
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    val name: String?
        get() {
            return requireArguments().getString("name")
        }

    @SuppressLint("CommitTransaction")
    private fun showTimePicker(timeTextView: TextView?) {
        val tx = timeTextView?.text.toString()
        val hour: Int
        hour = try {
            tx.substring(0, 2).toInt()
        } catch (e: Exception) {
            0
        }
        val min: Int
        min = try {
            tx.substring(3, 5).toInt()
        } catch (e: Exception) {
            0
        }
        val sec: Int
        sec = try {
            tx.substring(6).toInt()
        } catch (e: Exception) {
            0
        }
        if (timeTextView!!.id == R.id.durationEdit) {
            val mDurationFrag = TimePickerDialogFragment.newInstance(durationSet, hour, min, sec)
            val fm = requireActivity().supportFragmentManager
            mDurationFrag.show(fm.beginTransaction(), "dialog_time")
        }
    }

    val fragment: ExercisesInProgramFragment
        get() = this

    private val profil: Profile
        get() = mainActivity.currentProfile

    val machine: String
        get() = exerciseEdit.text.toString()

    private fun setCurrentExercise(machineStr: String) {
        if (machineStr.isEmpty()) {
            exerciseImage.setImageResource(R.drawable.ic_gym_bench_50dp) // Default image
            showExerciseTypeSelector(true)
            minMaxLayout.visibility = GONE
            return
        }
        val lMachine = mDbMachine.getMachine(machineStr)
        if (lMachine == null) {
            exerciseEdit.setText("")
            exerciseImage.setImageResource(R.drawable.ic_gym_bench_50dp) // Default image
            changeExerciseTypeUI(TYPE_FONTE, true)
            return
        }
        exerciseEdit.setText(lMachine.name)
        // Update exercise Image
        exerciseImage.setImageResource(R.drawable.ic_gym_bench_50dp) // Default image
        val imgUtil = ImageUtil()
        ImageUtil.setThumb(exerciseImage, imgUtil.getThumbPath(lMachine.picture)) // Overwrite image is there is one
        changeExerciseTypeUI(lMachine.type, false)
    }

    @SuppressLint("SetTextI18n")
    private fun refreshData() {
        if (programs!!.size != daoProgram.allProgramsNames?.size) {//only for program list refresh after add
            programs = daoProgram.allProgramsNames //update programs
            programId = daoProgram.getRecord(programs!![0])!!.id
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, programs!!)
            programSelect.adapter = adapter
            programSelect.onItemSelectedListener = object :
                AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>,
                                            view: View?, position: Int, id: Long) {
                    programId = daoProgram.getRecord(programs!![position])!!.id
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }

        daoExerciseInProgram.setProfile(profil)
        exercisesList = daoExerciseInProgram.getAllExerciseInProgram(programId).toMutableList()
        exercisesRecycler.adapter = ExerciseInProgramAdapter(requireContext(), exercisesList, itemClickDeleteRecord, null)
    }

    private fun showExerciseTypeSelector(displaySelector: Boolean) {
        if (displaySelector) exerciseTypeSelectorLayout.visibility = View.VISIBLE else exerciseTypeSelectorLayout.visibility = GONE
    }

    private fun changeExerciseTypeUI(pType: Int, displaySelector: Boolean) {
        showExerciseTypeSelector(displaySelector)
        when (pType) {
            TYPE_CARDIO -> {
                cardioSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.record_background_odd))
                bodybuildingSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                staticExerciseSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                serieCardView.visibility = GONE
                repetitionCardView.visibility = GONE
                weightCardView.visibility = GONE
                secondsCardView.visibility = GONE
                restTimeLayout.visibility = GONE
                distanceCardView.visibility = View.VISIBLE
                durationCardView.visibility = View.VISIBLE
                selectedType = TYPE_CARDIO
            }
            TYPE_STATIC -> {
                cardioSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                bodybuildingSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                staticExerciseSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.record_background_odd))
                serieCardView.visibility = View.VISIBLE
                repetitionCardView.visibility = GONE
                secondsCardView.visibility = View.VISIBLE
                weightCardView.visibility = View.VISIBLE
                restTimeLayout.visibility = View.VISIBLE
                distanceCardView.visibility = GONE
                durationCardView.visibility = GONE
                selectedType = TYPE_STATIC
            }
            TYPE_FONTE -> {
                cardioSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                bodybuildingSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.record_background_odd))
                staticExerciseSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                serieCardView.visibility = View.VISIBLE
                repetitionCardView.visibility = View.VISIBLE
                secondsCardView.visibility = GONE
                weightCardView.visibility = View.VISIBLE
                restTimeLayout.visibility = View.VISIBLE
                distanceCardView.visibility = GONE
                durationCardView.visibility = GONE
                selectedType = TYPE_FONTE
            }
            else -> {
                cardioSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                bodybuildingSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.record_background_odd))
                staticExerciseSelector.setBackgroundColor(ContextCompat.getColor(requireActivity().baseContext, R.color.background))
                serieCardView.visibility = View.VISIBLE
                repetitionCardView.visibility = View.VISIBLE
                secondsCardView.visibility = GONE
                weightCardView.visibility = View.VISIBLE
                restTimeLayout.visibility = View.VISIBLE
                distanceCardView.visibility = GONE
                durationCardView.visibility = GONE
                selectedType = TYPE_FONTE
            }
        }
    }

    private fun saveSharedParams() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
        val editor = sharedPref?.edit()
        editor?.putString("restTime", restTimeEdit.text.toString())
        editor?.putBoolean("restCheck", restTimeCheck.isChecked)
        editor?.putBoolean("showDetails", detailsLayout.isShown)
        editor?.apply()
    }

    private fun restoreSharedParams() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
        restTimeEdit.setText(sharedPref?.getString("restTime", ""))
        restTimeCheck.isChecked = sharedPref!!.getBoolean("restCheck", true)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) refreshData()
    }

    private fun hideKeyboard() {
        val inputMethodManager = Objects.requireNonNull(requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE)) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    companion object {
        /**
         * Create a new instance of DetailsFragment, initialized to
         * show the text at 'index'.
         */
        fun newInstance(name: String?, id: Int): ExercisesInProgramFragment {
            val f = ExercisesInProgramFragment()
            // Supply index input as an argument.
            val args = Bundle()
            args.putString("name", name)
            args.putInt("id", id)
            f.arguments = args
            return f
        }
    }
}
