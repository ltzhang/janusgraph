#ifndef STORE_TRANSACTION_H
#define STORE_TRANSACTION_H

#include <map>
#include <string>

class StoreTransaction {
private:
    std::map<std::string, bool> configuration;

public:
    StoreTransaction() {
        configuration["transactional"] = false;  // Default non-transactional
    }
    
    bool isTransactional() const {
        auto it = configuration.find("transactional");
        return it != configuration.end() ? it->second : false;
    }
    
    void setTransactional(bool transactional) {
        configuration["transactional"] = transactional;
    }
};

#endif