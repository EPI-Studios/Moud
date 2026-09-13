
function math.remap(value, fromMin, fromMax, toMin, toMax)
    if fromMax == fromMin then
        return toMin
    end
    return toMin + (value - fromMin) * (toMax - toMin) / (fromMax - fromMin)
end

function math.lerp(a, b, t)
    return a + (b - a) * t
end

function math.inverseLerp(a, b, value)
    if a == b then
        return 0
    end
    return (value - a) / (b - a)
end

function math.approach(value, target, step)
    if value < target then
        return math.min(value + step, target)
    end
    return math.max(value - step, target)
end

function math.smoothDamp(current, target, velocity, smoothTime, dt, maxSpeed)
    velocity = velocity or 0
    smoothTime = math.max(0.0001, smoothTime)
    local omega = 2 / smoothTime
    local x = omega * dt
    local exp = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x)
    local change = current - target
    if maxSpeed then
        local most = maxSpeed * smoothTime
        change = math.clamp(change, -most, most)
    end
    local goal = current - change
    local temp = (velocity + omega * change) * dt
    velocity = (velocity - omega * temp) * exp
    local out = goal + (change + temp) * exp
    if (target - current > 0) == (out > target) then
        out = target
        velocity = (out - target) / dt
    end
    return out, velocity
end

function math.fbm(x, y, z, octaves, lacunarity, gain)
    octaves = octaves or 4
    lacunarity = lacunarity or 2
    gain = gain or 0.5
    local sum, amplitude, frequency, total = 0, 1, 1, 0
    for _ = 1, octaves do
        sum += math.noise(x * frequency, (y or 0) * frequency, (z or 0) * frequency) * amplitude
        total += amplitude
        amplitude *= gain
        frequency *= lacunarity
    end
    return sum / total
end

function math.bezier(points, t)
    local work = table.clone(points)
    for n = #work, 2, -1 do
        for i = 1, n - 1 do
            work[i] = work[i] + (work[i + 1] - work[i]) * t
        end
    end
    return work[1]
end

function math.catmullRom(points, t)
    local count = #points
    if count == 1 then
        return points[1]
    end
    local scaled = math.clamp(t, 0, 1) * (count - 1)
    local i = math.min(math.floor(scaled) + 1, count - 1)
    local u = scaled - (i - 1)
    local p0 = points[math.max(i - 1, 1)]
    local p1 = points[i]
    local p2 = points[i + 1]
    local p3 = points[math.min(i + 2, count)]
    local u2, u3 = u * u, u * u * u
    return (p1 * 2 + (p2 - p0) * u + (p0 * 2 - p1 * 5 + p2 * 4 - p3) * u2 + (p1 * 3 - p0 - p2 * 3 + p3) * u3) * 0.5
end

angle = {}

function angle.wrap(a)
    return (a + math.pi) % (2 * math.pi) - math.pi
end

function angle.delta(a, b)
    return angle.wrap(b - a)
end

function angle.lerp(a, b, t)
    return a + angle.delta(a, b) * t
end

random = {}

function random.range(min, max)
    return min + math.random() * (max - min)
end

function random.int(min, max)
    return math.random(min, max)
end

function random.chance(probability)
    return math.random() < probability
end

function random.pick(list)
    if #list == 0 then
        return nil
    end
    return list[math.random(1, #list)]
end

function random.shuffle(list)
    for i = #list, 2, -1 do
        local j = math.random(1, i)
        list[i], list[j] = list[j], list[i]
    end
    return list
end

function random.unit()
    local z = random.range(-1, 1)
    local a = random.range(0, 2 * math.pi)
    local r = math.sqrt(1 - z * z)
    return vec3(r * math.cos(a), z, r * math.sin(a))
end

function random.onSphere(radius)
    return random.unit() * radius
end

function random.inSphere(radius)
    return random.unit() * (radius * math.random() ^ (1 / 3))
end

function random.inCircle(radius)
    local a = random.range(0, 2 * math.pi)
    local r = radius * math.sqrt(math.random())
    return vec3(math.cos(a) * r, 0, math.sin(a) * r)
end

function random.inBox(size)
    return vec3(random.range(-size.x / 2, size.x / 2), random.range(-size.y / 2, size.y / 2), random.range(-size.z / 2, size.z / 2))
end
