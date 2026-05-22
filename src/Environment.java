import java.util.HashMap;
import java.util.Map;

// =============================================================================
// Environment.java — Scope chain for variable bindings at runtime
//
// Design rationale (Sebesta §5.3 — static scoping):
//   OSlang uses static (lexical) scoping. Variables are resolved by walking
//   up the scope chain from the current scope to the global scope.
//
//   Each scope is a Map<String, RuntimeValue>. Scopes are linked as a chain:
//     global scope → function scope → block scope → inner block scope
//
//   push() enters a new scope (called on every block, function, process body)
//   pop()  leaves the current scope (called when block/function ends)
//
// Lifetime rules (Sebesta §5.4):
//   - Static variables live in the global scope for the entire program lifetime
//   - Local variables are created on push() and destroyed on pop()
//   - Semaphore and process handles are reference types — the handle is copied
//     but the underlying SemaphoreValue/ProcessHandle object is shared
// =============================================================================

public class Environment {

    // -------------------------------------------------------------------------
    // One scope node in the chain
    // -------------------------------------------------------------------------
    private static class Scope {
        final Map<String, RuntimeValue> bindings = new HashMap<>();
        final Scope parent; // null for global scope

        Scope(Scope parent) {
            this.parent = parent;
        }
    }

    private Scope current;

    // -------------------------------------------------------------------------
    // Constructor — starts with an empty global scope
    // -------------------------------------------------------------------------
    public Environment() {
        this.current = new Scope(null); // global scope
    }

    // -------------------------------------------------------------------------
    // push() — enter a new inner scope
    // Called at: block start, function call, process body start
    // -------------------------------------------------------------------------
    public void push() {
        current = new Scope(current);
    }

    // -------------------------------------------------------------------------
    // pop() — leave the current scope, restore parent
    // Called at: block end, function return, process body end
    // -------------------------------------------------------------------------
    public void pop() {
        if (current.parent == null) {
            throw new RuntimeException("Environment error: cannot pop global scope");
        }
        current = current.parent;
    }

    // -------------------------------------------------------------------------
    // define() — declare a NEW variable in the CURRENT scope only
    // Called at: variable declaration (VarDeclStmtNode, StaticDeclNode)
    // Error if name already declared in THIS scope (shadowing outer scope is ok)
    // -------------------------------------------------------------------------
    public void define(String name, RuntimeValue value) {
        if (current.bindings.containsKey(name)) {
            throw new RuntimeException(
                "Runtime error: variable '" + name + "' is already declared in this scope");
        }
        current.bindings.put(name, value);
    }

    // -------------------------------------------------------------------------
    // assign() — update an EXISTING variable anywhere in the scope chain
    // Called at: assignment statement (AssignStmtNode)
    // Error if name not found anywhere in the chain
    // -------------------------------------------------------------------------
    public void assign(String name, RuntimeValue value) {
        Scope scope = findScope(name);
        if (scope == null) {
            throw new RuntimeException(
                "Runtime error: variable '" + name + "' is not declared");
        }
        scope.bindings.put(name, value);
    }

    // -------------------------------------------------------------------------
    // lookup() — get value of a variable anywhere in the scope chain
    // Called at: identifier expression (IdentNode)
    // Error if name not found anywhere in the chain
    // -------------------------------------------------------------------------
    public RuntimeValue lookup(String name) {
        Scope scope = findScope(name);
        if (scope == null) {
            throw new RuntimeException(
                "Runtime error: variable '" + name + "' is not declared");
        }
        return scope.bindings.get(name);
    }

    // -------------------------------------------------------------------------
    // isDefined() — check if a name exists anywhere in the scope chain
    // Used by the interpreter to check for enum members, function names, etc.
    // -------------------------------------------------------------------------
    public boolean isDefined(String name) {
        return findScope(name) != null;
    }

    // -------------------------------------------------------------------------
    // defineOrAssign() — used for semaphore and process pass-by-reference:
    // if name exists in current scope update it, otherwise define it fresh
    // -------------------------------------------------------------------------
    public void defineOrAssign(String name, RuntimeValue value) {
        if (current.bindings.containsKey(name)) {
            current.bindings.put(name, value);
        } else {
            current.bindings.put(name, value);
        }
    }

    // -------------------------------------------------------------------------
    // Private helper — walk up the scope chain to find the scope that holds name
    // Returns null if not found (callers throw the error with context)
    // -------------------------------------------------------------------------
    private Scope findScope(String name) {
        Scope scope = current;
        while (scope != null) {
            if (scope.bindings.containsKey(name)) {
                return scope;
            }
            scope = scope.parent;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // snapshotLocalBindings() / restoreLocalBindings()
    // Used by the simulation engine to persist per-process local variables
    // across ticks (Decision Option B — each process keeps its own locals).
    //
    // Before a process step:
    //   env.push()
    //   env.restoreLocalBindings(savedLocals)   // re-populate from snapshot
    // After the step:
    //   Map<String,RuntimeValue> saved = env.snapshotLocalBindings()
    //   env.pop()
    // -------------------------------------------------------------------------
    public Map<String, RuntimeValue> snapshotLocalBindings() {
        return new HashMap<>(current.bindings);
    }

    public void restoreLocalBindings(Map<String, RuntimeValue> snapshot) {
        current.bindings.putAll(snapshot);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Scope scope = current;
        int level = 0;
        while (scope != null) {
            sb.append("Scope ").append(level).append(": ").append(scope.bindings.keySet()).append("\n");
            scope = scope.parent;
            level++;
        }
        return sb.toString();
    }
}