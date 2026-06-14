import type {CSSProperties} from 'react'

type VisualBackdropVariant = 'auth' | 'content' | 'chat'

type VisualBackdropProps = {
    variant: VisualBackdropVariant
    particleCount?: number
}

const particleSeeds = [
    [8, 18, 0, 17],
    [16, 72, 1, 22],
    [24, 38, 2, 19],
    [31, 84, 3, 24],
    [39, 14, 4, 21],
    [46, 58, 5, 18],
    [53, 29, 6, 23],
    [61, 77, 7, 20],
    [68, 46, 8, 25],
    [74, 10, 9, 19],
    [82, 67, 10, 22],
    [89, 34, 11, 21],
    [12, 49, 12, 26],
    [21, 6, 13, 20],
    [36, 91, 14, 24],
    [49, 40, 15, 18],
    [57, 5, 16, 23],
    [66, 86, 17, 22],
    [77, 52, 18, 25],
    [93, 21, 19, 19],
    [5, 62, 20, 21],
    [28, 96, 21, 26],
    [43, 73, 22, 18],
    [71, 28, 23, 24],
    [86, 88, 24, 22],
    [95, 56, 25, 20],
    [18, 31, 26, 23],
    [63, 12, 27, 25],
]

export function VisualBackdrop({variant, particleCount = 0}: VisualBackdropProps) {
    const particles = particleSeeds.slice(0, Math.max(0, Math.min(particleCount, particleSeeds.length)))

    return (
        <div aria-hidden="true" className={`visual-backdrop ${variant}`}>
            <div className="visual-backdrop-grid"/>
            <div className="visual-backdrop-glow primary"/>
            <div className="visual-backdrop-glow secondary"/>
            <div className="visual-backdrop-glow tertiary"/>
            {particles.length > 0 && (
                <div className="visual-backdrop-particles">
                    {particles.map(([top, left, delay, duration], index) => (
                        <span
                            className="visual-backdrop-particle"
                            key={`${top}-${left}`}
                            style={{
                                '--particle-delay': `${delay * 0.25}s`,
                                '--particle-duration': `${duration}s`,
                                '--particle-left': `${left}%`,
                                '--particle-top': `${top}%`,
                                '--particle-size': `${index % 3 === 0 ? 3 : 2}px`,
                            } as CSSProperties}
                        />
                    ))}
                </div>
            )}
        </div>
    )
}
