extends RefCounted
## 与渲染解耦的对局门闩，所有引擎游戏共用。
enum State { IDLE, RUNNING, PAUSED, ENDED }
var state: State = State.IDLE
var round_index: int = 0
func start() -> void:
    round_index += 1
    state = State.RUNNING
func is_running() -> bool:
    return state == State.RUNNING
func pause() -> bool:
    if state != State.RUNNING:
        return false
    state = State.PAUSED
    return true
func resume() -> bool:
    if state != State.PAUSED:
        return false
    state = State.RUNNING
    return true
func finish() -> bool:
    if state != State.RUNNING and state != State.PAUSED:
        return false
    state = State.ENDED
    return true
