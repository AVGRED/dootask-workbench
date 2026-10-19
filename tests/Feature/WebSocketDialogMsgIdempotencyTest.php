<?php

namespace Tests\Feature;

use App\Exceptions\ApiException;
use App\Models\WebSocketDialogMsg;
use ReflectionMethod;
use Tests\TestCase;

/**
 * Chat delivered messages need a Swoole task worker, so the delivery path itself is
 * covered by the release checklist. What can be verified in CLI is the part that
 * protects retries: key validation and the "same key must mean the same message"
 * comparison, both used by milestone reminder replays.
 */
class WebSocketDialogMsgIdempotencyTest extends TestCase
{
    private function callPrivate(string $method, array $arguments): mixed
    {
        $reflection = new ReflectionMethod(WebSocketDialogMsg::class, $method);
        $reflection->setAccessible(true);
        return $reflection->invokeArgs(null, $arguments);
    }

    private function existingMessage(int $dialogId, string $type, int $sender, array $msg): WebSocketDialogMsg
    {
        $message = new WebSocketDialogMsg();
        $message->setRawAttributes([
            'dialog_id' => $dialogId,
            'type' => $type,
            'userid' => $sender,
            'msg' => json_encode($msg, JSON_UNESCAPED_UNICODE),
        ]);
        // getRawOriginal() reads the original snapshot, which a database row would
        // already have; a manually built model has to sync it explicitly.
        $message->syncOriginal();
        return $message;
    }

    public function test_idempotency_key_must_be_a_lowercase_sha256(): void
    {
        $this->expectException(ApiException::class);
        WebSocketDialogMsg::sendMsg(null, 1, 'notice', ['notice' => 'hi'], 1, false, false, true, null, 'short-key');
    }

    public function test_idempotency_key_cannot_be_combined_with_a_message_action(): void
    {
        $this->expectException(ApiException::class);
        WebSocketDialogMsg::sendMsg('update-1', 1, 'notice', ['notice' => 'hi'], 1, false, false, true, null, hash('sha256', 'k'));
    }

    public function test_payload_hash_ignores_key_order(): void
    {
        $left = $this->callPrivate('idempotencyPayloadHash', [['b' => 2, 'a' => ['y' => 1, 'x' => 2]]]);
        $right = $this->callPrivate('idempotencyPayloadHash', [['a' => ['x' => 2, 'y' => 1], 'b' => 2]]);

        $this->assertSame($left, $right);
    }

    public function test_payload_hash_changes_when_the_content_changes(): void
    {
        $this->assertNotSame(
            $this->callPrivate('idempotencyPayloadHash', [['notice' => 'hello']]),
            $this->callPrivate('idempotencyPayloadHash', [['notice' => 'hello!']])
        );
    }

    public function test_replay_matching_requires_the_same_dialog_type_sender_and_content(): void
    {
        $existing = $this->existingMessage(11, 'notice', 7, ['notice' => 'hello']);

        $this->assertTrue($this->callPrivate('matchesIdempotentMessage', [$existing, 11, 'notice', 7, ['notice' => 'hello']]));
        $this->assertFalse($this->callPrivate('matchesIdempotentMessage', [$existing, 12, 'notice', 7, ['notice' => 'hello']]));
        $this->assertFalse($this->callPrivate('matchesIdempotentMessage', [$existing, 11, 'text', 7, ['notice' => 'hello']]));
        $this->assertFalse($this->callPrivate('matchesIdempotentMessage', [$existing, 11, 'notice', 8, ['notice' => 'hello']]));
        $this->assertFalse($this->callPrivate('matchesIdempotentMessage', [$existing, 11, 'notice', 7, ['notice' => 'changed']]));
    }
}
