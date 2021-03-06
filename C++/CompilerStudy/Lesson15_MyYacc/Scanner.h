#ifndef SCANNER_H
#define SCANNER_H

struct Token
{
    int type;
};

class Scanner
{
public:
    Scanner(const string& src);
    ~Scanner();
    bool getNext(Token& token);
private:
    class ScannerImpl *m_impl;
};

#endif
