package utopiascript;

import java.util.List;

interface UtopiaScriptCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}
