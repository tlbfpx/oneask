import React, { useState, useEffect, useRef } from 'react';
import { Send, Bot, User, Sparkles } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { streamChat } from './services/api';
import './index.css';

const MessageBubble = ({ message }) => {
  const isUser = message.role === 'user';

  return (
    <div className={`message-container ${isUser ? 'message-user' : 'message-assistant'}`}>
      <div className="message-avatar">
        {isUser ? <User size={20} color="white" /> : <Bot size={20} color="white" />}
      </div>
      <div>
        {!isUser && message.routeName && (
          <div className="message-route-info">
            <Sparkles size={12} />
            <span>Routed to: {message.routeName}</span>
          </div>
        )}
        <div className="message-bubble markdown-body">
          {isUser ? (
            <p>{message.content}</p>
          ) : (
            <ReactMarkdown>{message.content}</ReactMarkdown>
          )}
        </div>
      </div>
    </div>
  );
};

const App = () => {
  const [messages, setMessages] = useState([
    {
      id: 1,
      role: 'assistant',
      content: '你好！我是 OneAsk 智能问答助手。你可以问我通用问题，或者我可以为您连接专业客服处理售后和地址查询。请问有什么我可以帮您的？',
      routeName: 'System'
    }
  ]);
  const [inputValue, setInputValue] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [sessionId] = useState(() => Math.random().toString(36).substring(7));
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isTyping]);

  const handleSend = () => {
    if (!inputValue.trim() || isTyping) return;

    const userMsg = {
      id: Date.now(),
      role: 'user',
      content: inputValue
    };

    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setIsTyping(true);

    const botMsgId = Date.now() + 1;
    setMessages(prev => [...prev, {
      id: botMsgId,
      role: 'assistant',
      content: '',
      routeName: 'Thinking...'
    }]);

    streamChat(
      userMsg.content,
      sessionId,
      (chunk) => {
        setIsTyping(false);
        setMessages(prev => prev.map(msg => {
          if (msg.id === botMsgId) {
            return {
              ...msg,
              content: msg.content + chunk.replace(/\\n/g, '\n'),
              routeName: 'Customer Service'
            };
          }
          return msg;
        }));
      },
      () => {
        setIsTyping(false);
      },
      (error) => {
        setIsTyping(false);
        setMessages(prev => prev.map(msg => {
          if (msg.id === botMsgId && !msg.content) {
            return { ...msg, content: '抱歉，系统暂时出现响应错误：' + error.message, routeName: 'Error' };
          }
          return msg;
        }));
      }
    );
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="app-container">
      <div className="chat-header">
        <div className="chat-title">
          <Sparkles color="var(--accent-primary)" />
          OneAsk AI
        </div>
        <div className="status-indicator">
          <div className="status-dot"></div>
          Agents Online
        </div>
      </div>

      <div className="chat-window">
        {messages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
        {isTyping && (
          <div className="message-container message-assistant">
            <div className="message-avatar">
              <Bot size={20} color="white" />
            </div>
            <div className="typing-indicator">
              <div className="typing-dot"></div>
              <div className="typing-dot"></div>
              <div className="typing-dot"></div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="input-area">
        <div className="input-container">
          <textarea
            className="chat-input"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入您的问题..."
            rows={1}
            disabled={isTyping}
          />
          <button
            className="send-button"
            onClick={handleSend}
            disabled={!inputValue.trim() || isTyping}
          >
            <Send size={18} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default App;
