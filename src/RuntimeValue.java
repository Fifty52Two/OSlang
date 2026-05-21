import java.util.LinkedList;
import java.util.Queue;

// =============================================================================
// RuntimeValue.java — Tagged union holding any OSlang value at runtime
//
// Design rationale (Sebesta §6.1):
//   Every variable in OSlang has a type determined at compile time by the
//   type checker. At runtime, each value is wrapped in a RuntimeValue so the
//   interpreter can carry the type tag alongside the actual data.
//
//   We use a single class with one field per possible type rather than
//   subclasses — simpler to construct and switch on in the interpreter.
// =============================================================================

public class RuntimeValue {

    // -------------------------------------------------------------------------
    // Type tag — matches OSlang's type system exactly
    // -------------------------------------------------------------------------
    public enum Type {
        INT,        // int literal, int variable
        FLOAT,      // float literal, float variable, int widened to float
        BOOL,       // bool literal, bool variable
        STRING,     // string literal, string variable
        ENUM,       // enum member value — carries type name + member name + ordinal
        SEMAPHORE,  // semaphore handle — carries counter + wait queue
        PROCESS,    // process instance handle — carries all process fields
        VOID        // return type of functions that don't return a value (future use)
    }

    public final Type type;

    // -------------------------------------------------------------------------
    // Primitive fields — only the field matching `type` is meaningful
    // -------------------------------------------------------------------------
    public final int     intVal;    // INT
    public final double  floatVal;  // FLOAT
    public final boolean boolVal;   // BOOL
    public final String  stringVal; // STRING

    // -------------------------------------------------------------------------
    // Enum fields — all three are set together for ENUM values
    // -------------------------------------------------------------------------
    public final String enumTypeName;   // e.g. "State"
    public final String enumMemberName; // e.g. "ready"
    public final int    enumOrdinal;    // e.g. 0  (assigned by type checker)

    // -------------------------------------------------------------------------
    // Complex fields
    // -------------------------------------------------------------------------
    public final SemaphoreValue semVal;  // SEMAPHORE
    public final ProcessHandle  procVal; // PROCESS

    // -------------------------------------------------------------------------
    // Static factory methods — one per type for clean construction
    // -------------------------------------------------------------------------

    public static RuntimeValue ofInt(int v) {
        return new RuntimeValue(Type.INT, v, 0, false, null, null, null, 0, null, null);
    }

    public static RuntimeValue ofFloat(double v) {
        return new RuntimeValue(Type.FLOAT, 0, v, false, null, null, null, 0, null, null);
    }

    public static RuntimeValue ofBool(boolean v) {
        return new RuntimeValue(Type.BOOL, 0, 0, v, null, null, null, 0, null, null);
    }

    public static RuntimeValue ofString(String v) {
        return new RuntimeValue(Type.STRING, 0, 0, false, v, null, null, 0, null, null);
    }

    public static RuntimeValue ofEnum(String typeName, String memberName, int ordinal) {
        return new RuntimeValue(Type.ENUM, 0, 0, false, null, typeName, memberName, ordinal, null, null);
    }

    public static RuntimeValue ofSemaphore(int initialCounter) {
        return new RuntimeValue(Type.SEMAPHORE, 0, 0, false, null, null, null, 0,
                new SemaphoreValue(initialCounter), null);
    }

    public static RuntimeValue ofProcess(ProcessHandle handle) {
        return new RuntimeValue(Type.PROCESS, 0, 0, false, null, null, null, 0, null, handle);
    }

    public static RuntimeValue ofVoid() {
        return new RuntimeValue(Type.VOID, 0, 0, false, null, null, null, 0, null, null);
    }

    // -------------------------------------------------------------------------
    // Private constructor — use factory methods above
    // -------------------------------------------------------------------------
    private RuntimeValue(Type type,
                         int intVal, double floatVal, boolean boolVal,
                         String stringVal,
                         String enumTypeName, String enumMemberName, int enumOrdinal,
                         SemaphoreValue semVal, ProcessHandle procVal) {
        this.type           = type;
        this.intVal         = intVal;
        this.floatVal       = floatVal;
        this.boolVal        = boolVal;
        this.stringVal      = stringVal;
        this.enumTypeName   = enumTypeName;
        this.enumMemberName = enumMemberName;
        this.enumOrdinal    = enumOrdinal;
        this.semVal         = semVal;
        this.procVal        = procVal;
    }

    // -------------------------------------------------------------------------
    // print() — what OSlang's print() statement outputs for this value
    // Decisions locked 20 May 2026:
    //   - enum  → member name string (not ordinal)
    //   - semaphore → current counter value as int
    // -------------------------------------------------------------------------
    public String toDisplayString() {
        switch (type) {
            case INT:       return String.valueOf(intVal);
            case FLOAT:     return String.valueOf(floatVal);
            case BOOL:      return String.valueOf(boolVal);
            case STRING:    return stringVal;
            case ENUM:      return enumMemberName;           // Decision §5.32
            case SEMAPHORE: return String.valueOf(semVal.counter); // Decision §5.35
            case PROCESS:   return "[process " + procVal.name + "]";
            default:        return "void";
        }
    }

    @Override
    public String toString() {
        return "RuntimeValue(" + type + ": " + toDisplayString() + ")";
    }

    // =========================================================================
    // SemaphoreValue — mutable, shared across all references (pass-by-reference)
    // Decision §5.27: semaphore is always passed by reference
    // =========================================================================
    public static class SemaphoreValue {
        public int           counter;
        public Queue<String> waitQueue; // names of blocked process instances

        public SemaphoreValue(int counter) {
            this.counter   = counter;
            this.waitQueue = new LinkedList<>();
        }
    }

    // =========================================================================
    // ProcessHandle — one instance of a process at runtime
    // Decision §5.33: add() creates a new independent instance with its own
    //                 local variables. Each instance has a unique instanceId.
    // =========================================================================
    public static class ProcessHandle {
        public final String name;         // declared process name (template name)
        public final int    instanceId;   // unique id — two P1 instances have id 0 and 1
        public int          burst;        // declared burst value
        public int          remainingBurst; // counts down each tick
        public int          arrival;      // tick at which this instance enters the ready queue
        public int          priority;     // 0 = lowest (default if omitted — Decision §5.30)
        public State        state;        // current state in the scheduler

        // RR-specific — tracks remaining quantum for this instance this turn
        public int remainingQuantum;

        public enum State { READY, RUNNING, BLOCKED, FINISHED }

        public ProcessHandle(String name, int instanceId,
                             int burst, int arrival, int priority) {
            this.name             = name;
            this.instanceId       = instanceId;
            this.burst            = burst;
            this.remainingBurst   = burst;
            this.arrival          = arrival;
            this.priority         = priority;
            this.state            = State.READY;
            this.remainingQuantum = 0;
        }

        // Display name used in trace output — e.g. "P1" or "P1#1" for second instance
        public String displayName() {
            return instanceId == 0 ? name : name + "#" + instanceId;
        }
    }
}