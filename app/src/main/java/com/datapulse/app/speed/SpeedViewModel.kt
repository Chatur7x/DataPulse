package com.datapulse.app.speed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datapulse.app.data.local.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SpeedUi(val running:Boolean=false,val progress:SpeedProgress=SpeedProgress(),val result:SpeedResult?=null,val error:String?=null)
@HiltViewModel class SpeedViewModel @Inject constructor(private val engine:SpeedEngine,private val db:AppDatabase):ViewModel() {
 private val mutable=MutableStateFlow(SpeedUi())
 val ui=mutable.asStateFlow()
 val history=db.speed().recent().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private var job:Job?=null
 init {viewModelScope.launch{db.speed().recent().collect{rows->
  val current=mutable.value
  if(!current.running && current.result!=null && rows.none{it.id==current.result.id})mutable.update{it.copy(result=null)}
 }}}
 fun start(large:Boolean) {
  if(job?.isActive==true)return
  mutable.value=SpeedUi(running=true)
  job=viewModelScope.launch {
   try {val result=engine.run(large){p->mutable.update{it.copy(progress=p)}};db.speed().insert(result);mutable.value=SpeedUi(result=result)}
   catch(e:CancellationException){mutable.value=SpeedUi(error="Test cancelled. Incomplete results were not saved.");throw e}
   catch(e:Exception){mutable.value=SpeedUi(error=e.message?:"The measurement could not complete. Check the connection and try again.")}
  }
 }
 fun cancel(){job?.cancel()}
}
