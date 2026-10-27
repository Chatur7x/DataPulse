package com.datapulse.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.MB_BYTES
import com.datapulse.app.data.local.DailySnapshot
import com.datapulse.app.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

fun DailySnapshot.toUsageDay()=UsageDay(date,
 if(mobileBytes>0)mobileBytes else mobileMbUsed.toLong()*MB_BYTES,
 if(wifiBytes>0)wifiBytes else wifiMbUsed.toLong()*MB_BYTES)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(repo: PulseRepository):ViewModel() {
 private val selected=MutableStateFlow(LocalDate.now())
 val date=selected.asStateFlow()
 fun select(date: LocalDate) { if(date<=LocalDate.now())selected.value=date }
 val day=selected.flatMapLatest { repo.day(it.toString()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
 val apps=selected.flatMapLatest { repo.dayApps(it.toString()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val history=repo.recentDays(3660).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val analysis=history.map { rows ->
  val today=LocalDate.now()
  val current=rows.filter { it.date>=today.minusDays(7).toString() && it.date<today.toString() }.map { it.toUsageDay() }
  val previous=rows.filter { it.date>=today.minusDays(14).toString() && it.date<today.minusDays(7).toString() }.map { it.toUsageDay() }
  analyzeUsage(current,previous)
 }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),analyzeUsage(emptyList()))
}
