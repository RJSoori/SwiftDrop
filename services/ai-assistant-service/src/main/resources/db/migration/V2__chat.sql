-- Customer chat history for the assistant. Rows are ordered by id: a user message and its reply share one transaction
-- (and so one now()), so created_at alone cannot order them.
CREATE TABLE ai.chat_sessions (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_chat_sessions_user ON ai.chat_sessions (user_id);

CREATE TABLE ai.chat_messages (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES ai.chat_sessions (id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL CHECK (role IN ('user', 'assistant', 'tool')),
    content    TEXT        NOT NULL,
    tool_name  VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_chat_messages_session ON ai.chat_messages (session_id, id);
