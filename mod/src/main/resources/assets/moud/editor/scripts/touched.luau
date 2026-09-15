local part = script.parent :: Part

part.touched:connect(function(other)
    print(other.name, "touched", part.name)
end)
