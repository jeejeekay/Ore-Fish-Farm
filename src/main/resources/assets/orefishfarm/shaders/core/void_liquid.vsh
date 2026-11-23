#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewDir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Position is the world offset from the camera, so this is the world direction looked along.
    viewDir = Position;
}
