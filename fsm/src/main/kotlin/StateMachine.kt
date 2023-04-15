import java.util.logging.Logger

class StateMachine<IState : Any, IEvent : Any, ISideEffect : Any> private constructor(){
    lateinit var name: String
    private lateinit var initialState: IState
    private lateinit var finalState: IState
    private lateinit var onTransition: (ISideEffect) -> Unit
    private lateinit var currentState: IState
    private val transitionsMap: HashMap<IState, MutableList<Transition>> = hashMapOf()

    class Transition(val on: IEvent, val to: IState, val effect: ISideEffect) {
        class Valid(val from: IState, val on: IEvent, val to: IState, val effect: ISideEffect)

        class Invalid(val from: IState, val on: IEvent)
    }

    open inner class New internal constructor(open val name: String) {
        fun <S : IState> initialState(
            state: S,
            f: WithInitialState.() -> StateMachine<IState, IEvent, ISideEffect>
        ): StateMachine<IState, IEvent, ISideEffect> {
            this@StateMachine.name = name
            initialState = state

            return f(WithInitialState())
        }
    }

    open inner class WithInitialState internal constructor()
        : New(name)
    {
        fun <S : IState> finalState(
            state: S,
            addStates: WithFinalState.() -> StateMachine<IState, IEvent, ISideEffect>
        ): StateMachine<IState, IEvent, ISideEffect> {
            finalState = state

            return addStates(WithFinalState())
        }
    }

    open inner class WithFinalState internal constructor() : WithInitialState()
    {
        fun state(state: IState, builder: StateBuilder.() -> Transition) {
            transitionsMap.getOrPut(state) { mutableListOf() }.add(builder(StateBuilder()))
        }

        fun onTransition(execute: (ISideEffect) -> Unit): StateMachine<IState, IEvent, ISideEffect> {
            onTransition = execute

            return this@StateMachine
        }
    }

    inner class StateBuilder {
        fun on(event: IEvent, builder: TransitionBuilder.() -> Transition) = builder(TransitionBuilder(event))
    }

    inner class TransitionBuilder(private val on: IEvent){
        fun transitTo(to: IState, sd: ISideEffect) = Transition(on, to, sd)
    }

    fun fire(e: IEvent){
        findTransition(e).also {


            logger.info("Event ${e::class.simpleName} fired!")
            logger.info("Starting side effect ${it.effect::class.simpleName}...")
            onTransition(it.effect)
            logger.info("Side effect ${it.effect::class.simpleName} finished!")
            logger.info("Transiting to ${currentState::class.simpleName} -> ${it.to::class.simpleName}")
            currentState = it.to
        }
    }

    private fun findTransition(e: IEvent): Transition =
        transitionsMap.getOrElse(currentState) { listOf() }.firstOrNull { it.on == e }?.let {
            Transition.Valid(currentState, e, it.to, it.effect)
        } ?: Transition.Invalid(currentState, e)

    companion object {
        private const val TAG = "StateMachine"
        private val logger = Logger.getLogger(TAG)

        fun <S : Any, E : Any, SE : Any> build(
            name: String,
            init: StateMachine<S, E, SE>.New.() -> StateMachine<S, E, SE>
        ) = init(New(name))
    }
}