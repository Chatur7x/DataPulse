package com.datapulse.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datapulse.app.data.local.AppDatabase
import com.datapulse.app.sim.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class SimPlansViewModel @Inject constructor(private val discovery:SimDiscovery,private val db:AppDatabase):ViewModel(){
 val state=MutableStateFlow(SimDiscoveryState())
 val busy=MutableStateFlow(false)
 val error=MutableStateFlow<String?>(null)
 val records=db.sims().all().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 fun refresh(){if(busy.value)return;viewModelScope.launch{busy.value=true;try{state.value=discovery.read()}finally{busy.value=false}}}
 fun save(record:SimRecharge,onSaved:()->Unit){viewModelScope.launch{
  error.value=null
  try{db.sims().insert(record);onSaved()}catch(_:Exception){error.value="Could not save the recharge. Please try again."}
 }}
 fun delete(id:Long){viewModelScope.launch{try{db.sims().delete(id)}catch(_:Exception){error.value="Could not remove this record. Please try again."}}}
}
