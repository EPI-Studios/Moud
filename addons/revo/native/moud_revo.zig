const std = @import("std");
const revo = @import("revo");
const erevo = @import("erevo");

comptime {
    _ = erevo;
    _ = revo.ffi;
}

export fn moud_revo_function(vm_ptr: *anyopaque, name: [*:0]const u8, fn_ptr: *const anyopaque) callconv(.c) revo.Data {
    const vm: *revo.vm.VM = @ptrCast(@alignCast(vm_ptr));
    const owned = vm.runtime.alloc.dupe(u8, std.mem.span(name)) catch return revo.Data.new.nil();
    const id = vm.functions.create(.{ .c_function = .{ .name = owned, .fn_ptr = @ptrCast(@alignCast(fn_ptr)) } }) catch return revo.Data.new.nil();
    return revo.Data.new.function(id);
}

export fn moud_revo_detach(on: bool) callconv(.c) void {
    revo.vm.exec.detach_main = on;
}

export fn moud_revo_pump(vm_ptr: *anyopaque) callconv(.c) bool {
    const vm: *revo.vm.VM = @ptrCast(@alignCast(vm_ptr));
    const outer = vm.sched.current_fiber;
    defer vm.sched.current_fiber = outer;
    vm.sched.wakeDueSleepers(vm.schedNowMonotonicNs()) catch return false;
    const failure = revo.vm.exec.runReadyFibers(vm) catch return false;
    return failure == null;
}

export fn moud_revo_chan(vm_ptr: *anyopaque) callconv(.c) revo.Data {
    const vm: *revo.vm.VM = @ptrCast(@alignCast(vm_ptr));
    const id = vm.sched.channelCreate(&vm.tables, 0) catch return revo.Data.new.nil();
    return vm.tableOfSlice(&[2]revo.Data{
        revo.Data.new.atom(revo.core_atoms.chan.atomId()),
        revo.Data.new.num(id),
    }) catch revo.Data.new.nil();
}

export fn moud_revo_send(vm_ptr: *anyopaque, chan: revo.Data, value: revo.Data) callconv(.c) bool {
    const vm: *revo.vm.VM = @ptrCast(@alignCast(vm_ptr));
    const table_id = chan.asTable() orelse return false;
    const id = vm.arrayGet(table_id, 1) orelse return false;
    const number = id.asNum() orelse return false;
    vm.sched.channelSend(@intFromFloat(number), value) catch return false;
    return true;
}

export fn moud_revo_fiber(vm_ptr: *anyopaque) callconv(.c) u64 {
    const vm: *revo.vm.VM = @ptrCast(@alignCast(vm_ptr));
    return @intCast(vm.sched.current_fiber);
}
