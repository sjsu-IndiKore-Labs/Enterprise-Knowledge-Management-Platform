import time

if __name__ == "__main__":
    print('{"level":"INFO","service":"ai-worker","message":"fixture worker started"}', flush=True)
    while True:
        time.sleep(30)
