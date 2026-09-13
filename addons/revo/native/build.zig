const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{ .default_target = .{ .abi = .gnu } });
    const optimize = b.standardOptimizeOption(.{});
    const revo = b.dependency("revo", .{ .target = target, .optimize = optimize });

    const mod = b.createModule(.{
        .root_source_file = b.path("moud_revo.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
        .imports = &.{
            .{ .name = "revo", .module = revo.module("revo") },
            .{ .name = "erevo", .module = revo.module("erevo") },
        },
    });
    const lib = b.addLibrary(.{ .name = "moudrevo", .root_module = mod, .linkage = .dynamic });
    b.installArtifact(lib);
}
