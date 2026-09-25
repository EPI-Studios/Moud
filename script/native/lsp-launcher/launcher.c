#include <windows.h>

static HANDLE heap;

static void *grow(void *block, SIZE_T size) {
    return block ? HeapReAlloc(heap, 0, block, size) : HeapAlloc(heap, 0, size);
}

typedef struct {
    WCHAR *text;
    SIZE_T length;
    SIZE_T capacity;
} Line;

static void put(Line *line, WCHAR c) {
    if (line->length + 2 > line->capacity) {
        line->capacity = line->capacity ? line->capacity * 2 : 256;
        line->text = grow(line->text, line->capacity * sizeof(WCHAR));
    }
    line->text[line->length++] = c;
    line->text[line->length] = 0;
}

static void puts_raw(Line *line, const WCHAR *text, SIZE_T length) {
    for (SIZE_T i = 0; i < length; i++) put(line, text[i]);
}

static void quote(Line *line, const WCHAR *arg, SIZE_T length) {
    if (line->length) put(line, L' ');
    int plain = length > 0;
    for (SIZE_T i = 0; i < length; i++) {
        WCHAR c = arg[i];
        if (c == L' ' || c == L'\t' || c == L'\n' || c == L'\v' || c == L'"') plain = 0;
    }
    if (plain) {
        puts_raw(line, arg, length);
        return;
    }
    put(line, L'"');
    SIZE_T slashes = 0;
    for (SIZE_T i = 0; i < length; i++) {
        WCHAR c = arg[i];
        if (c == L'\\') {
            slashes++;
            continue;
        }
        SIZE_T times = c == L'"' ? slashes * 2 + 1 : slashes;
        for (SIZE_T j = 0; j < times; j++) put(line, L'\\');
        slashes = 0;
        put(line, c);
    }
    for (SIZE_T j = 0; j < slashes * 2; j++) put(line, L'\\');
    put(line, L'"');
}

static const WCHAR *rest(const WCHAR *command) {
    if (*command == L'"') {
        command++;
        while (*command && *command != L'"') command++;
        if (*command) command++;
    } else {
        while (*command && *command != L' ' && *command != L'\t') command++;
    }
    while (*command == L' ' || *command == L'\t') command++;
    return command;
}

static WCHAR *config(const WCHAR *self, SIZE_T *count) {
    int slash = lstrlenW(self);
    while (slash > 0 && self[slash - 1] != L'\\') slash--;
    WCHAR *path = HeapAlloc(heap, 0, (slash + 16) * sizeof(WCHAR));
    if (!path) return NULL;
    lstrcpynW(path, self, slash + 1);
    lstrcpyW(path + slash, L"luau-lsp.cfg");
    HANDLE file = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, NULL, OPEN_EXISTING, 0, NULL);
    if (file == INVALID_HANDLE_VALUE) return NULL;
    DWORD size = GetFileSize(file, NULL);
    char *bytes = size == INVALID_FILE_SIZE ? NULL : HeapAlloc(heap, 0, size + 1);
    DWORD read = 0;
    BOOL ok = bytes && ReadFile(file, bytes, size, &read, NULL) && read == size;
    CloseHandle(file);
    if (!ok) return NULL;
    char *start = bytes;
    if (size >= 3 && (unsigned char)bytes[0] == 0xEF && (unsigned char)bytes[1] == 0xBB && (unsigned char)bytes[2] == 0xBF) {
        start += 3;
        size -= 3;
    }
    int wide = MultiByteToWideChar(CP_UTF8, 0, start, (int)size, NULL, 0);
    WCHAR *text = HeapAlloc(heap, 0, (wide + 1) * sizeof(WCHAR));
    if (!text) return NULL;
    MultiByteToWideChar(CP_UTF8, 0, start, (int)size, text, wide);
    text[wide] = 0;
    *count = (SIZE_T)wide;
    return text;
}

static void say(const char *text) {
    DWORD written;
    WriteFile(GetStdHandle(STD_ERROR_HANDLE), text, lstrlenA(text), &written, NULL);
}

static void inherit(DWORD which) {
    HANDLE handle = GetStdHandle(which);
    if (handle && handle != INVALID_HANDLE_VALUE) SetHandleInformation(handle, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT);
}

void start(void) {
    heap = GetProcessHeap();
    Line line;
    SecureZeroMemory(&line, sizeof(line));
    WCHAR *program = HeapAlloc(heap, HEAP_ZERO_MEMORY, 32768 * sizeof(WCHAR));
    if (!program) ExitProcess(1);
    WCHAR *self = HeapAlloc(heap, HEAP_ZERO_MEMORY, 32768 * sizeof(WCHAR));
    if (!self || GetModuleFileNameW(NULL, self, 32768) >= 32768) ExitProcess(1);
    SIZE_T count = 0;
    WCHAR *text = config(self, &count);
    if (text) {
        SIZE_T at = 0, index = 0;
        while (at < count) {
            SIZE_T end = at;
            while (end < count && text[end] != L'\n') end++;
            SIZE_T trimmed = end;
            if (trimmed > at && text[trimmed - 1] == L'\r') trimmed--;
            if (index == 0) {
                SIZE_T length = trimmed - at < 32767 ? trimmed - at : 32767;
                lstrcpynW(program, text + at, (int)length + 1);
                quote(&line, program, length);
            } else if (trimmed > at) {
                quote(&line, text + at, trimmed - at);
            }
            index++;
            at = end + 1;
        }
    }
    DWORD attributes = program[0] ? GetFileAttributesW(program) : INVALID_FILE_ATTRIBUTES;
    if (attributes == INVALID_FILE_ATTRIBUTES || (attributes & FILE_ATTRIBUTE_DIRECTORY)) {
        line.length = 0;
        WCHAR *path = HeapAlloc(heap, HEAP_ZERO_MEMORY, 32768 * sizeof(WCHAR));
        if (!path || !GetEnvironmentVariableW(L"PATH", path, 32768) || !SearchPathW(path, L"luau-lsp.exe", NULL, 32768, program, NULL) || !lstrcmpiW(program, self)) {
            say("luau-lsp not found\n");
            ExitProcess(1);
        }
        quote(&line, program, lstrlenW(program));
    }
    const WCHAR *args = rest(GetCommandLineW());
    if (*args) {
        put(&line, L' ');
        puts_raw(&line, args, lstrlenW(args));
    }
    inherit(STD_INPUT_HANDLE);
    inherit(STD_OUTPUT_HANDLE);
    inherit(STD_ERROR_HANDLE);
    STARTUPINFOW startup;
    SecureZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES;
    startup.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    startup.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    startup.hStdError = GetStdHandle(STD_ERROR_HANDLE);
    PROCESS_INFORMATION process;
    if (!CreateProcessW(program, line.text, NULL, NULL, TRUE, CREATE_NO_WINDOW | CREATE_SUSPENDED, NULL, NULL, &startup, &process)) {
        say("could not start luau-lsp\n");
        ExitProcess(1);
    }
    HANDLE job = CreateJobObjectW(NULL, NULL);
    if (job) {
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits;
        SecureZeroMemory(&limits, sizeof(limits));
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits));
        AssignProcessToJobObject(job, process.hProcess);
    }
    ResumeThread(process.hThread);
    CloseHandle(process.hThread);
    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD code = 1;
    GetExitCodeProcess(process.hProcess, &code);
    ExitProcess(code);
}
