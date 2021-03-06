#ifndef DYNAMIC_BITSET_H
#define DYNAMIC_BITSET_H

class DynamicBitset
{
public:
    DynamicBitset(): m_size(0) {}
    DynamicBitset(int n, bool b = false);
    //DynamicBitset(const DynamicBitset& o);
    //DynamicBitset& operator = (const DynamicBitset& o);
    //~DynamicBitset();

    bool test(int i) const;
    void set(int i);
    void reset(int i);
    void inverse();
    void clear(bool b = false);
    int size() const;
    bool any() const;
    bool all() const;

    vector<int> toInts() const;
    
    void intersectWith(const DynamicBitset& o);
    void unionWith(const DynamicBitset& o);
    void deferrenceWith(const DynamicBitset& o);

    const vector<int>& data() const;
    vector<int>& data();
    bool operator == (const DynamicBitset& o) const;
    bool operator != (const DynamicBitset& o) const;
    bool operator < (const DynamicBitset& o) const;
    void swap(DynamicBitset& o);
private:
    vector<int> m_bits;
    int m_size;
private:
    static const int INT_BIT_COUNT = sizeof(int) * 8;
};
inline DynamicBitset::DynamicBitset(int n, bool b):
    m_size(n)
{
    int v = b ? ~0 : 0;
    int intCnt = (n + INT_BIT_COUNT - 1) / INT_BIT_COUNT;
    m_bits.resize(intCnt, v);
}

inline bool DynamicBitset::test(int i) const
{
    return ((m_bits[i / INT_BIT_COUNT] >> (i % INT_BIT_COUNT)) & 1) == 1;
}
inline void DynamicBitset::set(int i)
{
    m_bits[i / INT_BIT_COUNT] |= 1 << i % INT_BIT_COUNT;
}
inline void DynamicBitset::reset(int i)
{
    m_bits[i / INT_BIT_COUNT] &= ~(1 << i % INT_BIT_COUNT);
}
inline void DynamicBitset::inverse()
{
    for (int i = 0; i < size(); ++i) {
        if (test(i)) reset(i);
        else set(i);
    }
}
inline void DynamicBitset::clear(bool b)
{
    int v = b ? ~0 : 0;
    for (int i = 0; i < (int)m_bits.size(); ++i) m_bits[i] = v;
}
inline int DynamicBitset::size() const
{
    return m_size;
}
inline bool DynamicBitset::any() const
{
    for (int i = 0; i < m_size; ++i) {
        if (test(i)) return true;
    }
    return false;
}
inline bool DynamicBitset::all() const
{
    for (int i = 0; i < m_size; ++i) {
        if (!test(i)) return false;
    }
    return true;
}

inline vector<int> DynamicBitset::toInts() const
{
    vector<int> r;
    for (int i = 0; i < m_size; ++i) {
        if (test(i)) r.push_back(i);
    }
    return r;
}

inline void DynamicBitset::intersectWith(const DynamicBitset& o)
{
    assert(m_size == o.m_size);
    for (int i = 0; i < m_size; ++i) {
        if (test(i) && o.test(i)) set(i);
        else reset(i);
    }
}
inline void DynamicBitset::unionWith(const DynamicBitset& o)
{
    assert(m_size == o.m_size);
    for (int i = 0; i < m_size; ++i) {
        if (test(i) || o.test(i)) set(i);
        else reset(i);
    }
}
inline void DynamicBitset::deferrenceWith(const DynamicBitset& o)
{
    assert(m_size == o.m_size);
    for (int i = 0; i < m_size; ++i) {
        if (test(i) && o.test(i)) reset(i);
    }
}
inline const vector<int>& DynamicBitset::data() const
{
    return m_bits;
}
inline vector<int>& DynamicBitset::data()
{
    return m_bits;
}
inline bool DynamicBitset::operator == (const DynamicBitset& o) const
{
    return m_bits == o.m_bits && m_size == o.m_size;
}
inline bool DynamicBitset::operator != (const DynamicBitset& o) const
{
    return !(*this == o);
}
inline bool DynamicBitset::operator < (const DynamicBitset& o) const
{
    if (m_size == o.m_size) return m_bits < o.m_bits;
    return m_size < o.m_size;
}
inline void DynamicBitset::swap(DynamicBitset& o)
{
    m_bits.swap(o.m_bits);
    std::swap(m_size, o.m_size);
}

#endif
