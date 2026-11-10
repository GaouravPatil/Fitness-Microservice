import { Float, PointMaterial, Points } from '@react-three/drei'
import { Canvas, useFrame } from '@react-three/fiber'
import { Suspense, useMemo, useRef } from 'react'
import * as THREE from 'three'

type Variant = 'hero' | 'ambient'

interface SceneProps {
  variant?: Variant
}

function makeParticles(count: number): Float32Array {
  const positions = new Float32Array(count * 3)
  for (let index = 0; index < count; index += 1) {
    const radius = 4.5 + Math.random() * 3.5
    const theta = Math.random() * Math.PI * 2
    const phi = Math.acos(2 * Math.random() - 1)
    positions[index * 3] = radius * Math.sin(phi) * Math.cos(theta)
    positions[index * 3 + 1] = (Math.random() - 0.5) * 7
    positions[index * 3 + 2] = radius * Math.cos(phi)
  }
  return positions
}

function ParticleField({ count }: { count: number }) {
  const positions = useMemo(() => makeParticles(count), [count])
  const group = useRef<THREE.Group>(null)

  useFrame((_, delta) => {
    if (group.current) group.current.rotation.y += delta * 0.012
  })

  return (
    <group ref={group}>
      <Points positions={positions} stride={3} frustumCulled>
        <PointMaterial
          transparent
          color="#cbbaff"
          size={0.022}
          sizeAttenuation
          depthWrite={false}
          opacity={0.62}
        />
      </Points>
    </group>
  )
}

function Orb({ ambient }: { ambient: boolean }) {
  const group = useRef<THREE.Group>(null)
  const position: [number, number, number] = ambient ? [2.9, -0.1, -1.2] : [0.6, 0.65, 0]
  const detail = ambient ? 2 : 3

  useFrame((state, delta) => {
    if (!group.current) return
    group.current.rotation.y = THREE.MathUtils.damp(
      group.current.rotation.y,
      state.pointer.x * 0.35,
      2.5,
      delta,
    )
    group.current.rotation.x = THREE.MathUtils.damp(
      group.current.rotation.x,
      state.pointer.y * -0.18,
      2.5,
      delta,
    )
  })

  return (
    <Float
      speed={ambient ? 0.8 : 1.25}
      rotationIntensity={0.25}
      floatIntensity={ambient ? 0.5 : 0.85}
    >
      <group ref={group} position={position} scale={ambient ? 1.25 : 1}>
        <mesh>
          <icosahedronGeometry args={[1, detail]} />
          <meshStandardMaterial
            color={ambient ? '#1f1b2c' : '#211c32'}
            metalness={0.68}
            roughness={0.3}
            flatShading
            transparent={ambient}
            opacity={ambient ? 0.5 : 1}
          />
        </mesh>
        <mesh scale={1.002}>
          <icosahedronGeometry args={[1, detail]} />
          <meshBasicMaterial
            color="#c3b2ff"
            wireframe
            transparent
            opacity={ambient ? 0.04 : 0.16}
            depthWrite={false}
          />
        </mesh>
        <mesh position={[1.18, 0.72, 0.5]} scale={0.038}>
          <sphereGeometry args={[1, 16, 16]} />
          <meshBasicMaterial color="#e1d8ff" />
        </mesh>
      </group>
    </Float>
  )
}

function Rings({ ambient }: { ambient: boolean }) {
  const group = useRef<THREE.Group>(null)
  const position: [number, number, number] = ambient ? [2.9, -0.1, -1.2] : [0.6, 0.65, 0]

  useFrame((_, delta) => {
    if (!group.current) return
    group.current.rotation.z += delta * 0.045
    group.current.rotation.x += delta * 0.008
  })

  if (ambient) return null

  return (
    <group
      ref={group}
      position={position}
      rotation={[1.28, 0.18, 0]}
      scale={1.35}
    >
      <mesh>
        <torusGeometry args={[2.15, 0.006, 12, 220]} />
        <meshBasicMaterial color="#cbbdff" transparent opacity={ambient ? 0.22 : 0.48} />
      </mesh>
      <mesh rotation={[0.85, 0.15, 0.4]}>
        <torusGeometry args={[2.55, 0.004, 12, 220]} />
        <meshBasicMaterial color="#f2efe7" transparent opacity={ambient ? 0.1 : 0.22} />
      </mesh>
    </group>
  )
}

function CameraRig() {
  useFrame(({ camera, pointer }, delta) => {
    camera.position.x = THREE.MathUtils.damp(camera.position.x, pointer.x * 0.3, 2, delta)
    camera.position.y = THREE.MathUtils.damp(camera.position.y, pointer.y * 0.16, 2, delta)
    camera.lookAt(0, 0, 0)
  })
  return null
}

export default function AuraScene({ variant = 'ambient' }: SceneProps) {
  const ambient = variant === 'ambient'

  return (
    <Canvas
      className="aura-canvas"
      dpr={[1, 1.5]}
      camera={{ position: [0, 0, 5.4], fov: 42 }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      frameloop="always"
    >
      <Suspense fallback={null}>
        <ambientLight intensity={0.6} />
        <directionalLight position={[3, 4, 5]} intensity={1.6} color="#f2edff" />
        <pointLight position={[-4, -2, 2]} intensity={7} distance={14} color="#7a5cff" />
        <Orb ambient={ambient} />
        <Rings ambient={ambient} />
        <ParticleField count={ambient ? 120 : 200} />
        <CameraRig />
      </Suspense>
    </Canvas>
  )
}
