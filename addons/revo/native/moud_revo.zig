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
