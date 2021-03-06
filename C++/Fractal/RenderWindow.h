
#ifndef RENDERWINDOW_H
#define RENDERWINDOW_H

#include <vector>
#include <string>

class RenderWindow
{
public:
    enum class MouseButton
    {
        None,
        Left,
        Right,
    };

public:
    RenderWindow(char const *title, int width, int height);
    virtual ~RenderWindow();

    void Run(char *argv[], int argc);

    int GetWidth() const { return mWidth; }
    int GetHeight() const { return mHeight; }
    char const* GetTitle() const { return mTitle.c_str(); }
    virtual void Resize(int width, int height)
    {
        mWidth = width;
        mHeight = height;
    }
    float GetFps() const { return mFps; }

    virtual void Setup() = 0;
    virtual void Cleanup() = 0;
    virtual bool RenderToBuffer() = 0;
    virtual void Render(int *buffer) = 0;
    virtual void KeyDown(int key) = 0;
    virtual void KeyUp(int key) = 0;
    virtual void MouseButtonDown(MouseButton button, float x, float y) = 0;
    virtual void MouseButtonUp(MouseButton button, float x, float y) = 0;
    virtual void MouseMove(float x, float y) = 0;
    virtual void Update(float elapse)
    {
        mElapseSeconds += elapse;
        ++mElapseFrames;
        if (mElapseSeconds > 1)
        {
            On1SecondElapse();
            mFps = mElapseFrames / mElapseSeconds;
            mElapseSeconds = 0;
            mElapseFrames = 0;
        }
    }
    virtual void On1SecondElapse()  {}

private:
    std::string mTitle;
    int mWidth, mHeight;
    float mElapseSeconds = 0;
    int mElapseFrames = 0;
    float mFps;
};

#endif 
