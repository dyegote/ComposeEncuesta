/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.compose.jetsurvey.survey

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

const val simpleDateFormatPattern = "EEE, MMM d"

class EncuestaViewModel(
    private val surveyRepository: EncuestaRepository,
    private val photoUriManager: PhotoUriManager) : ViewModel() {

    private val _uiState = MutableLiveData<EncuestaState>()
    val uiState: LiveData<EncuestaState>
        get() = _uiState

    var askForPermissions by mutableStateOf(true)
        private set

    private lateinit var encuestaInitialState: EncuestaState

    // Uri used to save photos taken with the camera
    private var uri: Uri? = null

    init {
        viewModelScope.launch {
            val survey = surveyRepository.getSurvey()

            // Create the default questions state based on the survey questions
            val questions: List<QuestionState> = survey.preguntas.mapIndexed { index, question ->
                val showPrevious = index > 0
                val showDone = index == survey.preguntas.size - 1
                QuestionState(
                    pregunta = question,
                    questionIndex = index,
                    totalQuestionsCount = survey.preguntas.size,
                    showPrevious = showPrevious,
                    showDone = showDone
                )
            }
            encuestaInitialState = EncuestaState.Questions(survey.title, questions)
            _uiState.value = encuestaInitialState
        }
    }

    fun computeResult(encuestaQuestions: EncuestaState.Questions) {
        val answers = encuestaQuestions.questionsState.mapNotNull { it.answer }
        val result = surveyRepository.getSurveyResult(answers)
        _uiState.value = EncuestaState.Result(encuestaQuestions.surveyTitle, result)
    }

    fun onDatePicked(questionId: Int, pickerSelection: Long?) {
        val selectedDate = Date().apply {
            time = pickerSelection ?: getCurrentDate(questionId)
        }
        val formattedDate =
            SimpleDateFormat(simpleDateFormatPattern, Locale.getDefault()).format(selectedDate)
        updateStateWithActionResult(questionId, SurveyActionResult.Date(formattedDate))
    }

    fun getCurrentDate(questionId: Int): Long {
        return getSelectedDate(questionId)
    }

    fun getUriToSaveImage(): Uri? {
        uri = photoUriManager.buildNewUri()
        return uri
    }

    fun onImageSaved() {
        uri?.let { uri ->
            getLatestQuestionId()?.let { questionId ->
                updateStateWithActionResult(questionId, SurveyActionResult.Photo(uri))
            }
        }
    }

    // TODO: Ideally this should be stored in the database
    fun doNotAskForPermissions() {
        askForPermissions = false
    }

    private fun updateStateWithActionResult(questionId: Int, result: SurveyActionResult) {
        val latestState = _uiState.value
        if (latestState != null && latestState is EncuestaState.Questions) {
            val question =
                latestState.questionsState.first { questionState ->
                    questionState.pregunta.id == questionId
                }
            question.answer = Answer.Action(result)
            question.enableNext = true
        }
    }

    private fun getLatestQuestionId(): Int? {
        val latestState = _uiState.value
        if (latestState != null && latestState is EncuestaState.Questions) {
            return latestState.questionsState[latestState.currentQuestionIndex].pregunta.id
        }
        return null
    }

    private fun getSelectedDate(questionId: Int): Long {
        val latestState = _uiState.value
        var ret = Date().time
        if (latestState != null && latestState is EncuestaState.Questions) {
            val question =
                latestState.questionsState.first { questionState ->
                    questionState.pregunta.id == questionId
                }
            val answer: Answer.Action? = question.answer as Answer.Action?
            if (answer != null && answer.result is SurveyActionResult.Date) {
                val formatter = SimpleDateFormat(simpleDateFormatPattern, Locale.ENGLISH)
                val formatted = formatter.parse(answer.result.date)
                if (formatted is Date)
                    ret = formatted.time
            }
        }
        return ret
    }
}

class SurveyViewModelFactory(
    private val photoUriManager: PhotoUriManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EncuestaViewModel::class.java)) {
            return EncuestaViewModel(EncuestaRepository, photoUriManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
