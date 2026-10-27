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
 @Volatile private var generation=0L
 init {viewModelScope.launch{db.speed().recent().collect{rows->
  val current=mutable.value
  val id=current.result?.id
  if(!current.running && id!=null && rows.none{it.id==id} && db.speed().findOnce(id)==null)
   mutable.update{if(it.result?.id==id)it.copy(result=null) else it}
 }}}
 fun start(large:Boolean) {
  if(job?.isActive==true)return
  val run=++generation
  mutable.value=SpeedUi(running=true)
  job=viewModelScope.launch {
   try {val result=engine.run(large){p->mutable.update{if(run==generation&&it.running)it.copy(progress=p) else it}};ensureActive();db.speed().insert(result);if(run==generation)mutable.value=SpeedUi(result=result)}
   catch(e:TimeoutCancellationException){if(run==generation)mutable.value=SpeedUi(error="Test exceeded 90 seconds. No incomplete result was saved; try again on a stable connection.")}
   catch(e:CancellationException){if(run==generation)mutable.value=SpeedUi(error="Test cancelled. Incomplete results were not saved.");throw e}
   catch(e:Exception){if(run==generation)mutable.value=SpeedUi(error=e.message?:"The measurement could not complete. Check the connection and try again.")}
  }
 }
 fun cancel(){if(job?.isActive==true){generation++;job?.cancel();mutable.value=SpeedUi(error="Test cancelled. Incomplete results were not saved.")}}
 fun dismissError(){if(!mutable.value.running)mutable.update{it.copy(error=null)}}
}
