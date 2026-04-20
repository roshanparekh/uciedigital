package edu.berkeley.cs.uciedigital.logphy

import edu.berkeley.cs.uciedigital.sideband._
import edu.berkeley.cs.uciedigital.interfaces._
import chisel3._
import chisel3.util._


case class AfeParams(
  sbSerializerRatio: Int = 1,
  sbWidth: Int = 1,
  mbSerializerRatio: Int = 32,
  mbLanes: Int = 16,

  clockPhaseSelBitWidth: Int = 5,
  vRefSelBitWidth: Int = 5,

  numLinkOps: Int = 16,

  STANDALONE: Boolean = true
)

object TimeoutConstants {
  def timeoutMap(mbSerializerRatio: Int, timeoutMs: Double = 0.008): Map[SpeedMode.Type, BigInt] = {

    // GT/s is divide by 2 because operating at DDR (half-rate clocking)
    Map(
      SpeedMode.speed4  -> (((2_000_000_000L / mbSerializerRatio) * timeoutMs).toLong),
      SpeedMode.speed8  -> (((4_000_000_000L / mbSerializerRatio) * timeoutMs).toLong),
      SpeedMode.speed12 -> (((6_000_000_000L / mbSerializerRatio) * timeoutMs).toLong),
      SpeedMode.speed16 -> (((8_000_000_000L / mbSerializerRatio) * timeoutMs).toLong),
      SpeedMode.speed24 -> (((12_000_000_000L / mbSerializerRatio) * timeoutMs).toLong)
    )
  }
}

object MBRxTxMode extends ChiselEnum {
  // Either send/receive RAW or process with valid framing
  val RAW, VALID_FRAME = Value
}

object MsgSource extends ChiselEnum {
  val PATTERN_GENERATOR, SB_MSG = Value
}

// BEGIN: Bundles
class SidebandCtrlIO extends Bundle {
  val txEn        = Output(Bool())
  val rxEn        = Output(Bool())
  val rxTxMode    = Output(SBRxTxMode())
  val sbSerDesRst = Output(Bool()) // TODO: Planning to toggle this high during the 4ms wait coming into reset  
}

class MainbandLaneCtrlIO (afeParams: AfeParams) extends Bundle {
  val txDataTriState = Output(Vec(afeParams.mbLanes, Bool()))
  val txClkTriState = Output(Bool())
  val txValidTriState = Output(Bool())
  val txTrackTriState = Output(Bool())            
  val rxDataEn = Output(Vec(afeParams.mbLanes, Bool()))
  val rxClkEn = Output(Bool())
  val rxValidEn = Output(Bool())
  val rxTrackEn = Output(Bool())
}
class PhyCtrlIO extends Bundle {
  val freqSel = Output(SpeedMode())
  // val vrefSel TODO: Need to add
  val pllLock = Input(Bool())
}

class SidebandLanes(sbMsgWidth: Int) extends Bundle {
  /*
    For internal logPHY IOs. As of UCIe 3.0, we don't use the sideband
    clock besides in the deserializer, so we don't include it here.
  */
  val data = Bits(sbMsgWidth.W)
}

class SidebandLaneIO(sbParams: SidebandParams) extends Bundle {  
  val tx = Decoupled(new SidebandLanes(sbParams.sbNodeMsgWidth))
  val rx = Flipped(Decoupled(new SidebandLanes(sbParams.sbNodeMsgWidth)))
}

class MainbandLanes(mbNumLanes: Int, mbSerializerRatio: Int) extends Bundle {
  val data    = Vec(mbNumLanes, Bits(mbSerializerRatio.W))
  val valid   = Bits(mbSerializerRatio.W)
  val clkP    = Bits(mbSerializerRatio.W)
  val clkN    = Bits(mbSerializerRatio.W)
  val trk     = Bits(mbSerializerRatio.W)
}

class MainbandLaneIO(afeParams: AfeParams) extends Bundle {
  val tx = Decoupled(
    new MainbandLanes(afeParams.mbLanes, afeParams.mbSerializerRatio))
  val rx = Flipped(Decoupled(
    new MainbandLanes(afeParams.mbLanes, afeParams.mbSerializerRatio)))
}

class SubFsmControlIO extends Bundle {
  val start = Input(Bool())
  val substateTransitioning = Output(Bool())
  val error = Output(Bool())
  val done = Output(Bool())
}
// END: Bundles

object LTState extends ChiselEnum {
  val sRESET, sSBINIT, sMBINIT, sMBTRAIN, sLINKINIT, sACTIVE, sPHYRETRAIN, sTRAINERROR, sL1_L2  
  = Value
}


// class LinkTrainingSM(sbParams: SidebandParams, afeParams: AfeParams, retryW: Int) extends Module {

//   // Variables
//   val mbSerializerRatio = afeParams.mbSerializerRatio
//   val timeoutMs = 0.008
//   val retryAmtW = retryW  // TODO: Need to put retryW into an object
//   val timeoutCyclesMax = 2000000  // TODO: Put into an object

//   val io = IO(new Bundle {
//     // IN



//     // OUT
//     val sbRxTxMode = Output(SBRxTxMode())

//     // Bundles with IN & OUT IOs
//     // sb lanes


//     val currentState = Output(LTState())  // Out to logphytop    
//     val retryTrainingAmt = Input(UInt(retryAmtW.W))  // comes from ucie dvsec (controller in logphy)
    


//     val trainingBypass = Input(Bool())
//     val selectStateBypass = Input(LTState())

//     val trainingTimedout = Output(Bool())

//     val pwrGood = Input(Bool())

//     val sidebandCtrlIo = new SidebandCtrlIO()
//     val mainbandCtrlIo = new MainbandLaneCtrlIO(afeParams)
//     val sidebandLaneIo = new SidebandLaneIO(sbParams)
//     val mainbandLaneIo = new MainbandLaneIO(afeParams)
//     val phyCtrlIo = new PhyCtrlIO()
//   })
  
//   // FSM state register
//   val currentState = RegInit(LTState.sRESET)
//   val nextState = WireInit(currentState)
//   currentState := nextState
  
//   // Timeout Logic -- Digital operates with divided mb clock to keep clock crossing at boundaries
//   val timeoutMapScala = TimeoutConstants.timeoutMap(mbSerializerRatio, timeoutMs)
//   val timeoutWidth = log2Ceil(timeoutMapScala.values.max)
//   val timeoutMapChisel: Map[SpeedMode.Type, UInt] = timeoutMapScala.map { case (mode, big) =>
//                                                       mode -> big.U(timeoutWidth.W)
//                                                     }  
//   val timeoutCounter = RegInit(0.U(timeoutWidth.W))
//   val timeoutCyclesMax = Wire(UInt(timeoutWidth.W))
//   val timeoutCntEn = Wire(Bool())       // disable next cycle
//   val timeoutCntReset = Wire(Bool())    // reset next cycle
//   val trainingTimedout = Wire(Bool())  
//   val resetMinWait = RegInit(false.B)    
//   val substateTransitioning = Wire(Bool())

//   timeoutCntEn := (currentState =/= LTState.sRESET) &&
//                   (currentState =/= LTState.sACTIVE) &&
//                   (currentState =/= LTState.sL1_L2) &&
//                   (currentState =/= LTState.sTRAINERROR)

//   substateTransitioning := false.B
//   timeoutCntReset := (nextState =/= currentState) || substateTransitioning
//   trainingTimedout := timeoutCounter === timeoutCyclesMax
  

//   // get correct timeout cycles based on PHY speed

//   // TODO: this looks wrong syntax
//   timeoutCyclesMax := MuxLookup(io.phyCtrlIo.freqSel, 
//                                 (timeoutMapScala.values.min - 1).U)(timeoutMapChisel.toSeq)
//   when(timeoutCntReset) {
//     timeoutCounter := 0.U
//   }.otherwise {
//     when(timeoutCntEn){
//       when(timeoutCounter =/= timeoutCyclesMax) {
//         timeoutCounter := timeoutCounter + 1.U               
//       }
//     }
//   }

//   // Wait a minimum of 4ms upon entering RESET   
//   when(timeoutCounter === (timeoutCyclesMax >> 2) && (currentState === LTState.sRESET)) {
//     resetMinWait := true.B        
//   }.elsewhen((currentState =/= LTState.sRESET) && (nextState === LTState.sRESET)) {
//     resetMinWait := false.B
//   }


//   // Training Retrigger Logic - 
//   // TODO: Implement after verifying core functionality
//   // val prevTrigger = RegInit(false.B)
//   // val trainingRetryCounter = RegInit(0.U(retryAmtW.W))
//   // val autoRetrain = Wire(Bool())
//   // val retryCounterEn = Wire(Bool())
//   // val retryAmtMax = Reg(UInt(retryAmtW.W))


//   // Sideband RXTX Control
//   val sbRxTxMode = Wire(SBRxTxMode())
//   sbRxTxMode := SBRxTxMode.PACKET   // Only RESET and SBINIT will use RAW, so default can be PACKET

//   // IO connections
//   io.trainingTimedout := trainingTimedout

//   io.sidebandCtrlIo.txEn := true.B
//   io.sidebandCtrlIo.rxEn := true.B
//   io.sidebandCtrlIo.rxTxMode := sbRxTxMode
//   io.sidebandCtrlIo.sbSerDesRst := false.B

//   io.mainbandCtrlIo.txDataTriState.foreach(_ := true.B)
//   io.mainbandCtrlIo.txClkTriState := true.B
//   io.mainbandCtrlIo.txValidTriState := true.B
//   io.mainbandCtrlIo.txTrackTriState := true.B
//   io.mainbandCtrlIo.rxDataEn.foreach(_ := false.B)
//   io.mainbandCtrlIo.rxClkEn := false.B
//   io.mainbandCtrlIo.rxValidEn := false.B
//   io.mainbandCtrlIo.rxTrackEn := false.B
//   timeoutFreqSel := SpeedMode.speed4
//   io.phyCtrlIo.freqSel := SpeedMode.speed4

//   // For the ready/valid for the lanes
//   io.sidebandLaneIo.rx.ready := false.B

  
//   // Remote triggered training
//   // Remote SBINIT pattern detection in LTState.sRESET only
//   val sbInitPatternCounter = RegInit(0.U(2.W))
//   val remoteTriggerTraining = Wire(Bool())
//   val sbInitClkPattern = BigInt("5555555555555555", 16).U(64.W) // 0b0101_0101_..._0101

//   remoteTriggerTraining := sbInitPatternCounter === 2.U


//   // RDI triggered training
//   val rdiTriggerTraining = Wire(Bool())
//   rdiTriggerTraining := false.B
//   // TODO: Do after creating RDI state machine
//   //  Adapter triggers Link Training on the RDI (RDI status is Reset and there is a NOP to Active
//   //  transition on the state request)

//   // SW triggered training
//   val swTriggerTraining = Wire(Bool())
//   swTriggerTraining := false.B 
//   // TODO: Do logic after adding DVSEC registers
//   //  Software writes 1 to Start UCIe Link Training bit in UCIe Link Control register in the UCIe Link
//   //  DVSEC (see Section 9.5.1.5)

//   val triggerTraining = Wire(Bool())
//   triggerTraining := swTriggerTraining || rdiTriggerTraining || remoteTriggerTraining


//   // TODO: Current state logic needs to be added based on FSM module state

//   // TODO: Registers hold the lane reversal code, update when signal width change and reset when going into reset

//   // Substate FSMs
//   // TODO: need to signal reset when LTSM transitions TrainError --> Reset
//   val subFsmModuleReset = (reset.asBool || trainingTimedout).asAsyncReset

//   // TODO: Need to fix the reset logic
//   // Sideband Routing logic from the modules (TODO: use this to do the aribtration when valid is high)
//   // Need to add the defaults
//   val requesterSbLaneIo = Wire(new SidebandLaneIO(sbParams))
//   val responderSbLaneIo = Wire(new SidebandLaneIO(sbParams))

//   // Training Basic Operations
//   // TODO: These wires will have defaults, and then mux one side with the modules in the fsm and these wires connect to the 
//   // training operation modules 
//   // TX-initiated D2C Point Test
//   val txPtTestRequester = Module(new TxD2CPointTestRequester(afeParams, sbParams))

//   // TX-initiated D2C Eye Width Sweep

//   // RX-initiated D2C Point Test

//   // RX-initiated D2C Eye Width Sweep

//   // THESE IOs might need to be flipped
//   val txPtTestReqIntfIo = Wire(new TxInitPtTestRequesterInterfaceIO(afeParams))
//   val txEyeSweepReqIntfIo = Wire(new TxInitEyeWidthSweepRequesterInterfaceIO(afeParams))
//   val rxPtTestReqIntfIo = Wire(new RxInitPtTestRequesterInterfaceIO(afeParams))
//   val rxEyeSweepReqIntfIo = Wire(new RxInitEyeWidthSweepRequesterInterfaceIO(afeParams))
//   val txPtTestRespIntfIo = Wire(new TxInitPtTestResponderInterfaceIO())
//   val txEyeSweepRespIntfIo = Wire(new TxInitEyeWidthSweepResponderInterfaceIO())
//   val rxPtTestRespIntfIo = Wire(new RxInitPtTestResponderInterfaceIO())
//   val rxEyeSweepRespIntfIo = Wire(new RxInitEyeWidthSweepResponderInterfaceIO())


//   // PatternReader and PatternWriter Interfacing IO (TODO: need to mux between using signal is high)
//   // will connect out to the PatternWriter and PatternReader (flipped)
//   val patternWriterIntfIo = Wire(new PatternWriterIO())
//   val patternReaderInftIo = Wire(new PatternReaderIO(afeParams.mbLanes))
  

//   // TODO: Go through these signals and make sure they are all used

//   // SBInit
//   val sbInitSM = Module(new SBInitSM(sbParams, timeoutCyclesMax)) // TODO: need to change the reset signal

//   sbInitSM.io.fsmCtrl
//   sbInitSM.io.sbRxTxMode
//   sbInitSM.io.requesterSbLaneIo
//   sbInitSM.io.responderSbLaneIo

//   // MBInit
//   val mbInitSM = Module(new MBInitSM(afeParams, sbParams)) // TODO: need to change the reset signal
//   mbInitSM.io.mbInitCalDone := false.B      // TODO: Need to decide on if SW or HW (can be connected directly)
//   mbInitSM.io.localPhySettings :=           // TODO: From registers, can add them in LTSM (can be connected directly)
//   mbInitSM.io.mbInitCalStart                // with mbInitCalDone
//   mbInitSM.io.currentState                  // TODO: use this for proper current state logic
//   mbInitSM.io.applyLaneReversal             // TODO: Used by mb data path
//   mbInitSM.io.localFunctionalLanes          // TODO: need to add the lane code registers; if remote is all lanes functional, then local takes presidence
//   mbInitSM.io.txWidthChanged
//   mbInitSM.io.remoteFunctionalLanes
//   mbInitSM.io.rxWidthChanged
//   mbInitSM.io.interoperableParamsNotFound   // TODO: used to escalate an error (mbInit.io.fsmCtrl.error also goes high)
//   mbInitSM.io.negotiatedPhySettings         // Need registers that have the settings
//   mbInitSM.io.fsmCtrl
//   mbInitSM.io.patternWriterIo
//   mbInitSM.io.patternReaderIo
//   mbInitSM.io.requesterSbLaneIo
//   mbInitSM.io.responderSbLaneIo
//   mbInitSM.io.txPtTestReqInterfaceIo
//   mbInitSM.io.txPtTestRespInterfaceIo
//   mbInitSM.io.usingPatternWriter
//   mbInitSM.io.usingPatternReader

//   // MBTrain
//   val mbTrainSM = Module (new MBTrainSM(afeParams, sbParams)) // TODO: need to change reset signal
//   // The parameters are known from outside (DVSEC, or elaboration) see where they come from
//   mbInitSM.io.goToState // has valid
//   mbInitSM.io.negotiatedMaxDataRate
//   mbInitSM.io.pllLock
//   mbInitSM.io.mbTrainTxSelfCalDone
//   mbInitSM.io.mbTrainRxClkCalDone
//   mbInitSM.io.phyInRetrain
//   mbInitSM.io.interpretBy8Lane
//   mbInitSM.io.maxErrorThresholdPerLane
//   mbInitSM.io.changeInRuntimeLinkCtrlRegs
//   mbInitSM.io.currLocalTxFunctionalLanes
//   mbInitSM.io.currRemoteTxFunctionalLanes

//   mbInitSM.io.currentState
//   mbInitSM.io.mbLaneCtrlIo
//   mbInitSM.io.freqSel
//   mbInitSM.io.mbTrainTxSelfCalStart
//   mbInitSM.io.mbTrainRxClkCalStart
//   mbInitSM.io.doElectricalIdleTx
//   mbInitSM.io.clearPhyInRetrainFlag
//   mbInitSM.io.txWidthChanged
//   mbInitSM.io.newLocalFunctionalLanes
//   mbInitSM.io.rxClkCalSendFwClkPattern
//   mbInitSM.io.rxClkCalSendTrkPattern
//   mbInitSM.io.newRemoteFunctionalLanes
//   mbInitSM.io.rxWidthChanged
//   mbInitSM.io.doElectricalIdleRx

//   mbInitSM.io.fsmCtrl
//   mbInitSM.io.requesterSbLaneIo
//   mbInitSM.io.responderSbLaneIo
//   mbInitSM.io.txPtTestReqIntfIo
//   mbInitSM.io.txEyeSweepReqIntfIo
//   mbInitSM.io.rxPtTestReqIntfIo
//   mbInitSM.io.rxEyeSweepReqIntfIo
//   mbInitSM.io.txPtTestRespIntfIo
//   mbInitSM.io.txEyeSweepRespIntfIo
//   mbInitSM.io.rxPtTestRespIntfIo
//   mbInitSM.io.rxEyeSweepRespIntfIo

//   // PhyRetrain
//   // TODO: need to add the rdi logic, and retrain encoding logic
//   val phyRetrainSbModule = Module(new PhyRetrainSidebandHandshake(sbParams)) // TODO: likely need to change reset on this
//   phyRetrainSbModule.io.startRdiMsgExch
//   phyRetrainSbModule.io.startPhyRetrainMsgExch
//   phyRetrainSbModule.io.requesterLocalRetrainEncoding
//   phyRetrainSbModule.io.waitForRemoteRequest
//   phyRetrainSbModule.io.sendRdiRetrainResp
//   phyRetrainSbModule.io.responderLocalRetrainEncoding

//   phyRetrainSbModule.io.requesterRemoteRetrainEncoding
//   phyRetrainSbModule.io.rdiRespRecieved
//   phyRetrainSbModule.io.responderLocalRetrainEncoding
//   phyRetrainSbModule.io.remoteRequestedRetrain

//   phyRetrainSbModule.io.done
//   phyRetrainSbModule.io.requesterSbLaneIo
//   phyRetrainSbModule.io.responderSbLaneIo

//   // TrainError
//   // If local timedout, is there another 8ms timer for the response wait. If so the repurpose the timer
//   // and log the timeout flag and trigger the fatal error
//   val trainErrorRequester = Module(new TrainErrorRequester(sbParams))
//   trainErrorRequester.io.sendReq
//   trainErrorRequester.io.resetSbMsg

//   trainErrorRequester.io.done

//   trainErrorRequester.io.sbLaneIo

//   val trainErrorResponder = Module(new TrainErrorResponder(sbParams))
//   trainErrorResponder.io.wakeUp
//   trainErrorResponder.io.resetSbMsg
//   trainErrorResponder.io.sendResp

//   trainErrorResponder.io.remoteRequestingTrainError
//   trainErrorResponder.io.done

//   trainErrorResponder.io.sbLaneIo
  
//   // TODO: Have error handling logic from the modules here
//   // TODO: need to use signal from trainerror responder and likely do a reset on the modules and transition to trainerror
//   // TODO: when coming into reset, we reset any state register in the LTSM
//   // TODO: note we may have to let sb and mb messages through if there are pending
//   // TODO: Need to make sure to classify the errors appropriately, correctable, uncorrectable fatel, uncorrectable non-fatel
//   val errorDetected = Wire(Bool())  

//   // State Machine
//   switch(currentState) {
//     is(LTState.sRESET) {            
//       /*  
//       Default signals:
//         Data, Valid, Clock TX are tri-state (tristate == 1)
//         Data, Valid, Clock RX are disabled (en == 0)
//         Sideband TX is enabled (en == 1)
//         Sideband RX is enabled (en == 1)                
//         Set Mainband Clock Speed to lowest (4 GT/s)
//       */    

//       sbRxTxMode := SBRxTxMode.RAW      
//       io.sidebandLaneIo.rx.ready := true.B
//       when(io.sidebandLaneIo.rx.valid) {
//         when(io.sidebandLaneIo.rx.bits.data(63,0) === sbInitClkPattern) {
//           when(sbInitPatternCounter =/= 2.U) {
//             sbInitPatternCounter := sbInitPatternCounter + 1.U
//           }
//         }.otherwise { 
//           when(sbInitPatternCounter === 1.U) { // pattern not consecutively seen, so reset counter
//             sbInitPatternCounter := 0.U
//           }
//         }
//       }
 
//       when(io.pwrGood && io.phyCtrlIo.pllLock && resetMinWait && triggerTraining) {
//         nextState := LTState.sSBINIT
//         sbInitPatternCounter := 0.U
//       }.otherwise {
//         nextState := LTState.sRESET
//       }
//     }
//     is(LTState.sSBINIT) {        
//       // SBInit doesn't use mainband ctrl IO, so defaults are kept

//       sbInitSM.io.fsmCtrl.start := !trainingTimedout && !sbInitSM.io.fsmCtrl.done
//       sbRxTxMode := sbInitSM.io.fsmCtrl.sbRxTxMode
//       requesterSbLaneIo <> sbInitSM.io.requesterSbLaneIo
//       responderSbLaneIo <> sbInitSM.io.responderSbLaneIo

//       when(trainingTimedout) {
//         nextState := LTState.sTRAINERROR
//       }.elsewhen(sbInitSM.io.fsmCtrl.done) {
//         nextState := LTState.sMBINIT
//       }      
//     }
//     is(LTState.sMBINIT) {
//       mbInitSM.io.fsmCtrl.start := !trainingTimedout && !mbInitSM.io.fsmCtrl.done
//       requesterSbLaneIo <> mbInitSM.io.requesterSbLaneIo
//       responderSbLaneIo <> mbInitSM.io.responderSbLaneIo
//       txPtTestReqInterfaceIo <> mbInitSM.io.txPtTestReqInterfaceIo
//       txPtTestRespInterfaceIo <> mbInitSM.io.txPtTestRespInterfaceIo
//       io.mainbandCtrlIo := mbInitSM.io.mbLaneCtrlIo

//       when(trainingTimedout || errorDetected) {
//         nextState := LTState.sTRAINERROR
//       }.elsewhen(mbInitSM.io.fsmCtrl.done) {
//         nextState := LTState.sMBTRAIN
//       }
//     }
//     is(LTState.sMBTRAIN) {
      

//       // goes to linkinit
//       // phyretrain
//       // trainerror
//     }
//     is(LTState.sLINKINIT) {
      
//       // goes to active
//       // trainerror
//     } 
//     is(LTState.sACTIVE) {
      
//       // phyretrain
//       // l1
//       // l2
//       // trainerror
//     } 
//     is(LTState.sL1_L2) {
//       // Currently 
    
//       // mbtrain
//       // reset
//       // trainerror
//     }   
//     is(LTState.sPHYRETRAIN) {
      
//       // mbtrain
//       // trainerror
//     } 
//     is(LTState.sTRAINERROR) {
      
//       // reset
//     } 
//   }
// }


class LinkTrainingStateMachine(sbParams: SidebandParams, afeParams: AfeParams, retryW: Int = 4) extends Module {
  private val timeoutMs = 0.008

  val io = IO(new Bundle {
    // Global controls
    val startTraining = Input(Bool())
    val pwrGood = Input(Bool())
    val trainingBypass = Input(Bool())
    val selectStateBypass = Input(LTState())

    // MBInit inputs
    val mbInitCalDone = Input(Bool())
    val localPhySettings = Flipped(Valid(new MbInitPHYParamExchangeIO()))

    // MBTrain inputs
    val mbTrainGoToState = Flipped(Valid(MBTrainGoToState()))
    val mbTrainTxSelfCalDone = Input(Bool())
    val mbTrainRxClkCalDone = Input(Bool())
    val phyInRetrain = Input(Bool())
    val interpretBy8Lane = Input(Bool())
    val maxErrorThresholdPerLane = Input(UInt(16.W))
    val changeInRuntimeLinkCtrlRegs = Input(Bool())

    // LinkInit handshake gates (from RDI/top-level policy)
    val linkInitSafeToSendReq = Input(Bool())
    val linkInitSafeToSendResp = Input(Bool())

    // Status / control outputs
    val currentState = Output(LTState())
    val trainingTimedout = Output(Bool())
    val error = Output(Bool())
    val done = Output(Bool())

    // Re-export key negotiated/training outputs
    val negotiatedPhySettings = Valid(new MbInitPHYParamExchangeIO())
    val mbTrainState = Output(MBTrainState())

    // Physical interfaces
    val sidebandCtrlIo = new SidebandCtrlIO()
    val mainbandCtrlIo = new MainbandLaneCtrlIO(afeParams)
    val sidebandLaneIo = new SidebandLaneIO(sbParams)
    val phyCtrlIo = new PhyCtrlIO()
  })

  val currentStateReg = RegInit(LTState.sRESET)
  val nextState = WireInit(currentStateReg)
  currentStateReg := nextState
  io.currentState := currentStateReg

  // Timeouts are implemented only for standard package rates up to 24 GT/s.
  val timeoutMapScala = TimeoutConstants.timeoutMap(afeParams.mbSerializerRatio, timeoutMs)
  val timeoutWidth = log2Ceil(timeoutMapScala.values.max.toInt + 1)
  val timeoutCounter = RegInit(0.U(timeoutWidth.W))

  val timeoutFreqSel = WireDefault(SpeedMode.speed4)
  val timeoutCyclesMax = Wire(UInt(timeoutWidth.W))
  timeoutCyclesMax := timeoutMapScala(SpeedMode.speed24).U
  switch(timeoutFreqSel) {
    is(SpeedMode.speed4) { timeoutCyclesMax := timeoutMapScala(SpeedMode.speed4).U }
    is(SpeedMode.speed8) { timeoutCyclesMax := timeoutMapScala(SpeedMode.speed8).U }
    is(SpeedMode.speed12) { timeoutCyclesMax := timeoutMapScala(SpeedMode.speed12).U }
    is(SpeedMode.speed16) { timeoutCyclesMax := timeoutMapScala(SpeedMode.speed16).U }
    is(SpeedMode.speed24) { timeoutCyclesMax := timeoutMapScala(SpeedMode.speed24).U }
  }

  val timeoutCntEn = (currentStateReg =/= LTState.sRESET) &&
                     (currentStateReg =/= LTState.sACTIVE) &&
                     (currentStateReg =/= LTState.sL1_L2) &&
                     (currentStateReg =/= LTState.sTRAINERROR)
  val trainingTimedout = timeoutCounter === timeoutCyclesMax
  io.trainingTimedout := trainingTimedout

  when(nextState =/= currentStateReg) {
    timeoutCounter := 0.U
  }.elsewhen(timeoutCntEn && (timeoutCounter =/= timeoutCyclesMax)) {
    timeoutCounter := timeoutCounter + 1.U
  }

  // RESET minimum wait (4ms of the 8ms timeout window)
  val resetMinWait = RegInit(false.B)
  when((currentStateReg === LTState.sRESET) && (timeoutCounter === (timeoutCyclesMax >> 1))) {
    resetMinWait := true.B
  }.elsewhen((currentStateReg =/= LTState.sRESET) && (nextState === LTState.sRESET)) {
    resetMinWait := false.B
  }

  // Sub-FSM modules
  val sbInitSM = Module(new SBInitSM(sbParams, timeoutMapScala(SpeedMode.speed24).toInt))
  val mbInitSM = Module(new MBInitSM(afeParams, sbParams))
  val mbTrainSM = Module(new MBTrainSM(afeParams, sbParams))
  val linkInitSM = Module(new LinkInitSidebandHandshake(sbParams))
  val trainErrorRequester = Module(new TrainErrorRequester(sbParams))
  val trainErrorResponder = Module(new TrainErrorResponder(sbParams))

  // Default starts/gates
  sbInitSM.io.fsmCtrl.start := false.B
  mbInitSM.io.fsmCtrl.start := false.B
  mbTrainSM.io.fsmCtrl.start := false.B
  linkInitSM.io.start := false.B
  trainErrorRequester.io.sendReq := false.B
  trainErrorResponder.io.sendResp := false.B

  // MBInit wiring
  mbInitSM.io.mbInitCalDone := io.mbInitCalDone
  mbInitSM.io.localPhySettings := io.localPhySettings

  // MBTrain wiring
  mbTrainSM.io.goToState := io.mbTrainGoToState
  mbTrainSM.io.mbTrainTxSelfCalDone := io.mbTrainTxSelfCalDone
  mbTrainSM.io.mbTrainRxClkCalDone := io.mbTrainRxClkCalDone
  mbTrainSM.io.phyInRetrain := io.phyInRetrain
  mbTrainSM.io.interpretBy8Lane := io.interpretBy8Lane
  mbTrainSM.io.maxErrorThresholdPerLane := io.maxErrorThresholdPerLane
  mbTrainSM.io.changeInRuntimeLinkCtrlRegs := io.changeInRuntimeLinkCtrlRegs
  val negotiatedRate = WireDefault(SpeedMode.speed4)
  when(mbInitSM.io.negotiatedPhySettings.valid) {
    switch(mbInitSM.io.negotiatedPhySettings.bits.maxDataRate(2, 0)) {
      is(SpeedMode.speed4.asUInt) { negotiatedRate := SpeedMode.speed4 }
      is(SpeedMode.speed8.asUInt) { negotiatedRate := SpeedMode.speed8 }
      is(SpeedMode.speed12.asUInt) { negotiatedRate := SpeedMode.speed12 }
      is(SpeedMode.speed16.asUInt) { negotiatedRate := SpeedMode.speed16 }
      is(SpeedMode.speed24.asUInt) { negotiatedRate := SpeedMode.speed24 }
    }
  }
  mbTrainSM.io.negotiatedMaxDataRate := negotiatedRate
  mbTrainSM.io.pllLock := io.phyCtrlIo.pllLock
  mbTrainSM.io.currLocalTxFunctionalLanes := mbInitSM.io.localFunctionalLanes
  mbTrainSM.io.currRemoteTxFunctionalLanes := mbInitSM.io.remoteFunctionalLanes

  // Keep requester/responder test/calibration interfaces disconnected for now.
  mbInitSM.io.patternWriterIo := DontCare
  mbInitSM.io.patternReaderIo := DontCare
  mbInitSM.io.txPtTestReqInterfaceIo := DontCare
  mbInitSM.io.txPtTestRespInterfaceIo := DontCare

  mbTrainSM.io.txPtTestReqIntfIo := DontCare
  mbTrainSM.io.txEyeSweepReqIntfIo := DontCare
  mbTrainSM.io.rxPtTestReqIntfIo := DontCare
  mbTrainSM.io.rxEyeSweepReqIntfIo := DontCare
  mbTrainSM.io.txPtTestRespIntfIo := DontCare
  mbTrainSM.io.txEyeSweepRespIntfIo := DontCare
  mbTrainSM.io.rxPtTestRespIntfIo := DontCare
  mbTrainSM.io.rxEyeSweepRespIntfIo := DontCare

  linkInitSM.io.safeToSendReq := io.linkInitSafeToSendReq
  linkInitSM.io.safeToSendResp := io.linkInitSafeToSendResp

  trainErrorRequester.io.resetSbMsg := currentStateReg =/= LTState.sTRAINERROR
  trainErrorResponder.io.resetSbMsg := currentStateReg =/= LTState.sTRAINERROR
  trainErrorResponder.io.wakeUp := (currentStateReg =/= LTState.sRESET) && (currentStateReg =/= LTState.sTRAINERROR)

  // Sideband routing helpers: one requester + one responder interface per active state.
  def disableSb(port: SidebandLaneIO): Unit = {
    port.rx.valid := false.B
    port.rx.bits.data := 0.U
    port.tx.ready := false.B
  }

  disableSb(sbInitSM.io.requesterSbLaneIo)
  disableSb(sbInitSM.io.responderSbLaneIo)
  disableSb(mbInitSM.io.requesterSbLaneIo)
  disableSb(mbInitSM.io.responderSbLaneIo)
  disableSb(mbTrainSM.io.requesterSbLaneIo)
  disableSb(mbTrainSM.io.responderSbLaneIo)
  disableSb(linkInitSM.io.requesterSbLaneIo)
  disableSb(linkInitSM.io.responderSbLaneIo)
  disableSb(trainErrorRequester.io.sbLaneIo)
  disableSb(trainErrorResponder.io.sbLaneIo)

  val sbTxValid = WireDefault(false.B)
  val sbTxBits = WireDefault(0.U(sbParams.sbNodeMsgWidth.W))
  val sbRxReady = WireDefault(false.B)

  def routeSb(req: SidebandLaneIO, resp: SidebandLaneIO, enable: Bool): Unit = {
    when(enable) {
      req.rx.valid := io.sidebandLaneIo.rx.valid
      req.rx.bits.data := io.sidebandLaneIo.rx.bits.data
      resp.rx.valid := io.sidebandLaneIo.rx.valid
      resp.rx.bits.data := io.sidebandLaneIo.rx.bits.data

      sbRxReady := req.rx.ready || resp.rx.ready

      val reqWins = req.tx.valid
      sbTxValid := req.tx.valid || resp.tx.valid
      sbTxBits := Mux(reqWins, req.tx.bits.data, resp.tx.bits.data)
      req.tx.ready := io.sidebandLaneIo.tx.ready && req.tx.valid
      resp.tx.ready := io.sidebandLaneIo.tx.ready && !req.tx.valid && resp.tx.valid
    }
  }

  routeSb(sbInitSM.io.requesterSbLaneIo, sbInitSM.io.responderSbLaneIo, currentStateReg === LTState.sSBINIT)
  routeSb(mbInitSM.io.requesterSbLaneIo, mbInitSM.io.responderSbLaneIo, currentStateReg === LTState.sMBINIT)
  routeSb(mbTrainSM.io.requesterSbLaneIo, mbTrainSM.io.responderSbLaneIo, currentStateReg === LTState.sMBTRAIN)
  routeSb(linkInitSM.io.requesterSbLaneIo, linkInitSM.io.responderSbLaneIo, currentStateReg === LTState.sLINKINIT)
  routeSb(trainErrorRequester.io.sbLaneIo, trainErrorResponder.io.sbLaneIo, currentStateReg === LTState.sTRAINERROR)

  io.sidebandLaneIo.tx.valid := sbTxValid
  io.sidebandLaneIo.tx.bits.data := sbTxBits
  io.sidebandLaneIo.rx.ready := sbRxReady

  // Defaults for top-level controls
  io.sidebandCtrlIo.txEn := true.B
  io.sidebandCtrlIo.rxEn := true.B
  io.sidebandCtrlIo.rxTxMode := Mux(currentStateReg === LTState.sSBINIT, sbInitSM.io.sbRxTxMode, SBRxTxMode.PACKET)
  io.sidebandCtrlIo.sbSerDesRst := false.B

  io.mainbandCtrlIo.txDataTriState.foreach(_ := true.B)
  io.mainbandCtrlIo.txClkTriState := true.B
  io.mainbandCtrlIo.txValidTriState := true.B
  io.mainbandCtrlIo.txTrackTriState := true.B
  io.mainbandCtrlIo.rxDataEn.foreach(_ := false.B)
  io.mainbandCtrlIo.rxClkEn := false.B
  io.mainbandCtrlIo.rxValidEn := false.B
  io.mainbandCtrlIo.rxTrackEn := false.B

  timeoutFreqSel := SpeedMode.speed4
  io.phyCtrlIo.freqSel := timeoutFreqSel

  when(currentStateReg === LTState.sMBINIT) {
    io.mainbandCtrlIo := mbInitSM.io.mbLaneCtrlIo
  }.elsewhen(currentStateReg === LTState.sMBTRAIN) {
    io.mainbandCtrlIo := mbTrainSM.io.mbLaneCtrlIo
    when(mbTrainSM.io.freqSel.valid) {
      timeoutFreqSel := mbTrainSM.io.freqSel.bits
      io.phyCtrlIo.freqSel := mbTrainSM.io.freqSel.bits
    }
  }

  // State transitions
  switch(currentStateReg) {
    is(LTState.sRESET) {
      when(io.trainingBypass) {
        nextState := io.selectStateBypass
      }.elsewhen(io.pwrGood && io.phyCtrlIo.pllLock && resetMinWait && io.startTraining) {
        nextState := LTState.sSBINIT
      }
    }

    is(LTState.sSBINIT) {
      sbInitSM.io.fsmCtrl.start := !trainingTimedout && !sbInitSM.io.fsmCtrl.done
      when(trainingTimedout) {
        nextState := LTState.sTRAINERROR
      }.elsewhen(sbInitSM.io.fsmCtrl.done) {
        nextState := LTState.sMBINIT
      }
    }

    is(LTState.sMBINIT) {
      mbInitSM.io.fsmCtrl.start := !trainingTimedout && !mbInitSM.io.fsmCtrl.done
      when(trainingTimedout || mbInitSM.io.fsmCtrl.error) {
        nextState := LTState.sTRAINERROR
      }.elsewhen(mbInitSM.io.fsmCtrl.done) {
        nextState := LTState.sMBTRAIN
      }
    }

    is(LTState.sMBTRAIN) {
      mbTrainSM.io.fsmCtrl.start := !trainingTimedout && !mbTrainSM.io.fsmCtrl.done
      when(trainingTimedout || mbTrainSM.io.fsmCtrl.error) {
        nextState := LTState.sTRAINERROR
      }.elsewhen(mbTrainSM.io.fsmCtrl.done) {
        when(mbTrainSM.io.currentState === MBTrainState.sTOPHYRETRAIN) {
          nextState := LTState.sPHYRETRAIN
        }.otherwise {
          nextState := LTState.sLINKINIT
        }
      }
    }

    is(LTState.sLINKINIT) {
      linkInitSM.io.start := !trainingTimedout && !linkInitSM.io.done
      when(trainingTimedout) {
        nextState := LTState.sTRAINERROR
      }.elsewhen(linkInitSM.io.done) {
        nextState := LTState.sACTIVE
      }
    }

    is(LTState.sACTIVE) {
      when(io.phyInRetrain || io.changeInRuntimeLinkCtrlRegs) {
        nextState := LTState.sPHYRETRAIN
      }
    }

    is(LTState.sPHYRETRAIN) {
      // Re-enter MBTRAIN and let MBTrain go-to-state policy control sequence.
      nextState := LTState.sMBTRAIN
    }

    is(LTState.sL1_L2) {
      nextState := LTState.sMBTRAIN
    }

    is(LTState.sTRAINERROR) {
      trainErrorRequester.io.sendReq := true.B
      trainErrorResponder.io.sendResp := true.B
      when(trainErrorRequester.io.done || trainErrorResponder.io.done) {
        nextState := LTState.sRESET
      }
    }
  }

  io.error := (currentStateReg === LTState.sTRAINERROR) || mbInitSM.io.fsmCtrl.error || mbTrainSM.io.fsmCtrl.error
  io.done := currentStateReg === LTState.sACTIVE
  io.negotiatedPhySettings := mbInitSM.io.negotiatedPhySettings
  io.mbTrainState := mbTrainSM.io.currentState
}

object MainLinkTrainingStateMachine extends App {
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new LinkTrainingStateMachine(new SidebandParams(), new AfeParams()),
    args = Array("-td", "./generatedVerilog/logphy/"),
    firtoolOpts = Array(
      "-O=debug",
      "-g",
      "--disable-all-randomization",
      "--strip-debug-info",
      "--lowering-options=disallowLocalVariables",
    ),
  )
}
