import {requestJson} from '../../services/request'

type PasswordEncryptionKeyResponse = {
    keyId: string
    algorithm: string
    publicKey: string
}

export type PasswordTransportPayload = {
    encryptedPassword: string
    passwordKeyId: string
}

let passwordKeyPromise: Promise<PasswordEncryptionKeyResponse> | null = null

export function clearPasswordKeyCache() {
    passwordKeyPromise = null
}

export async function encryptPasswordForTransport(password: string): Promise<PasswordTransportPayload> {
    if (!globalThis.crypto?.subtle) {
        throw new Error('Password encryption requires WebCrypto in a secure browser context')
    }
    const key = await currentPasswordKey()
    const cryptoKey = await globalThis.crypto.subtle.importKey(
        'spki',
        base64ToArrayBuffer(key.publicKey),
        {name: 'RSA-OAEP', hash: 'SHA-256'},
        false,
        ['encrypt'],
    )
    const encrypted = await globalThis.crypto.subtle.encrypt(
        {name: 'RSA-OAEP'},
        cryptoKey,
        new TextEncoder().encode(password),
    )
    return {
        encryptedPassword: arrayBufferToBase64(encrypted),
        passwordKeyId: key.keyId,
    }
}

async function currentPasswordKey() {
    if (!passwordKeyPromise) {
        passwordKeyPromise = requestJson<PasswordEncryptionKeyResponse>('/api/auth/password-key', {auth: false})
            .then((key) => {
                if (key.algorithm !== 'RSA-OAEP-256') {
                    throw new Error('Unsupported password encryption algorithm')
                }
                return key
            })
            .catch((error) => {
                passwordKeyPromise = null
                throw error
            })
    }
    return passwordKeyPromise
}

function base64ToArrayBuffer(value: string) {
    const binary = atob(value)
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index)
    }
    return bytes.buffer
}

function arrayBufferToBase64(buffer: ArrayBuffer) {
    const bytes = new Uint8Array(buffer)
    const chunks: string[] = []
    const chunkSize = 0x8000
    for (let index = 0; index < bytes.length; index += chunkSize) {
        chunks.push(String.fromCharCode(...bytes.subarray(index, index + chunkSize)))
    }
    return btoa(chunks.join(''))
}
