import axios from 'axios';

const API_BASE_URL = 'http://localhost:8082/api';

/**
 * Sends a chat message to the LLM Routing Agent
 * @param {string} message - The user message
 * @param {string} sessionId - The session ID
 * @param {function} onMessage - Callback for the response
 * @param {function} onComplete - Callback when complete
 * @param {function} onError - Callback when an error occurs
 */
export const streamChat = (message, sessionId, onMessage, onComplete, onError) => {
    const url = `${API_BASE_URL}/chat`;

    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            message: message,
            sessionId: sessionId
        })
    }).then(async (response) => {
        if (!response.ok) {
            throw new Error(`HTTP Error: ${response.status}`);
        }

        const contentType = response.headers.get('content-type');
        console.log('Response content-type:', contentType);
        
        if (contentType && contentType.includes('application/json')) {
            const data = await response.json();
            console.log('Response data:', data);
            if (data.reply) {
                console.log('Calling onMessage with reply:', data.reply.substring(0, 100));
                onMessage(data.reply);
            }
            if (data.routedTo) {
                onMessage.routingInfo = data.routedTo;
            }
            onComplete();
        } else {
            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        const dataStr = line.substring(5).trim();
                        if (dataStr) {
                            onMessage(dataStr);
                        }
                    }
                }
            }
            onComplete();
        }
    }).catch((error) => {
        console.error("Request error:", error);
        onError(error);
    });
};
